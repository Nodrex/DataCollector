package com.nodrex.eventscollector

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * A generic collector that asynchronously waits for a complete set of values,
 * as defined by the properties of a provided data class, and assembles them into
 * a new instance of that class.
 *
 * This class is useful for scenarios where multiple independent and asynchronous
 * data sources need to be resolved before an action can be taken. It uses a
 * combination of Kotlin Flow's `zip` operator and Reflection to dynamically
 * build a collection pipeline.
 *
 * The collector can be configured to run once or continuously. Upon completion or cancellation,
 * it releases all internal resources.
 *
 * ### Lifecycle and Concurrency
 * This class manages its own `CoroutineScope`. A new `Job()` is added to the scope's
 * context (`dispatcher + Job() + exceptionHandler`) to create a self-contained, cancellable
 * lifecycle.
 *
 * @param T The type of the data class to be collected and constructed. Must have a primary constructor.
 * @param classType data class `T` used as a template to determine the required properties and their types via reflection.
 * @param onResult A callback lambda that is invoked with the result. It receives either a populated instance of `T` on success, or a `Throwable` on failure.
 * @param collectionCount The number of times to collect a complete set of events. Defaults to `null`, which means it will collect continuously until `cancel()` is called. A value of `1` will cause it to collect once and then stop.
 * @param dispatcher The `CoroutineDispatcher` on which the collection and result callback will be executed. Defaults to `Dispatchers.Default`.
 */
class EventsCollector<T : Any> constructor(
    private val classType: KClass<T>,
    private val onResult: (result: T?, error: Throwable?) -> Unit,
    private val collectionCount: Int? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    //TODO consider scope as well so it will be dismissed automatically when that scope will be canceled, for exmaple viewmodelscope or lifecyclescope
) {

    companion object {
        /**
         * Creates and starts an [EventsCollector] that collects a specific number of times.
         *
         * This factory function provides a clean, type-safe API for creating a collector. Thanks to
         * the use of `inline` and `reified`, you can specify the target data class `T` as a
         * generic parameter (e.g., `start<UserData>(...)`) without needing to pass the
         * class reference (`UserData::class`) manually.
         *
         * The collector will automatically stop and release its resources after the specified
         * `collectionCount` has been reached.
         *
         * @param T The data class type to be collected and instantiated. It must be `reified`,
         * meaning its type information is preserved at runtime.
         * @param onResult The callback lambda that will be invoked with either a populated instance
         * of `T` on success or a `Throwable` on failure.
         * @param collectionCount The exact number of times a complete set of events should be
         * collected before the collector automatically stops.
         * @param dispatcher The `CoroutineDispatcher` on which the collection and result callback
         * will be executed. Defaults to `Dispatchers.Default`.
         * @return A new instance of [EventsCollector] that has already started its collection process.
         */
        inline fun <reified T : Any> start(
            noinline onResult: (result: T?, error: Throwable?) -> Unit,
            collectionCount: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ) = EventsCollector(T::class, onResult, collectionCount, dispatcher)
    }

    /**
     * A handler for uncaught exceptions that occur within the collector's coroutine scope.
     *
     * This handler ensures that any unexpected failure (e.g., from an `emit` call or an
     * issue during the `zip` operation) is gracefully managed.
     *
     * Upon catching an exception, it performs two actions:
     * 1. Invokes the `onResult` callback with a null result and the captured `throwable`.
     * 2. Calls `cancel()` to immediately stop all operations and release all resources.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Util.loge("EventsCollector - Exception -> ${throwable.stackTrace.joinToString("\n")}\n\t will deliver empty result, please check params in emit!")
        onResult(null, throwable)
        cancel()
    }

    /**
     * ### Lifecycle and Concurrency
     * This class manages its own `CoroutineScope`. A new `Job()` is added to the scope's
     * context (`dispatcher + Job() + exceptionHandler`) to create a self-contained, cancellable
     * lifecycle. This is the cornerstone of **structured concurrency**. It ensures that when `cancel()`
     * is called on this instance, the parent `Job` is cancelled, which in turn reliably stops all
     * child coroutines launched within this scope (including the main collector and all `emit` jobs)
     * without affecting any external scope.
     */
    private var scope: CoroutineScope? = CoroutineScope(dispatcher + Job() + exceptionHandler) //TODO i need supervisor job cause if i batch of fata will be fault it should continue collecting others and just send this fault as error, but continue listening others
    private var propertyHandlers: MutableList<PropertyHandler<T>>?
    private var collectorJob: Job? = null
    private var emitterJob: Job? = null

    /**
     * Private helper class to link a reflected property to its corresponding SharedFlow.
     */
    private class PropertyHandler<T : Any>(var property: KProperty1<T, *>?) {
        var flow: MutableSharedFlow<Any>? = MutableSharedFlow(replay = 1)
    }

    init {
        logInitialization()
        //require(dataClassInstance::class.isData) { "Only data classes are supported." } //TODO need to implement
        propertyHandlers = classType.declaredMemberProperties.map {
            PropertyHandler(it)
        }.toMutableList()
        startCollector()
    }

    /**
     * Sets up the flow zipping logic and launches the main collection coroutine.
     * This is an internal function called upon initialization.
     *
     *
     * Sets up the dynamic zipping of all property flows and launches the main collection coroutine.
     *
     * A simple loop of `zip` calls is not sufficient as it would produce a nested `Pair`
     * structure (e.g., `Pair<Pair<A, B>, C>`). To achieve a flat `List<Any>` of results,
     * a two-step functional pattern is used:
     *
     * 1.  The first flow is transformed from a `Flow<Any>` to a `Flow<List<Any>>` using
     * `.map { listOf(it) }`. This creates the initial list to be built upon.
     * 2.  The remaining flows are then folded onto this initial flow. In each step, `.zip`
     * combines the accumulating `Flow<List<Any>>` with the next `Flow<Any>`, and the
     * lambda (`{ list, newItem -> list + newItem }`) appends the new item, producing a
     * new, larger `Flow<List<Any>>` for the next iteration.
     */
    private fun startCollector() {
        val currentFlowHolders = propertyHandlers ?: return

        if (currentFlowHolders.isEmpty() || currentFlowHolders.all { it.flow == null }) {
            onResult.invoke(
                null,
                IllegalStateException("Collector was initialized with invalid properties or flows.")
            )
            cancel()
            return
        }

        collectorJob = scope?.launch {
            // 1. MODIFICATION: Transform each flow to emit a Pair of (Property, Value)
            val contextualFlows = currentFlowHolders.map { handler ->
                handler.flow!!.map { value ->
                    Pair(handler.property!!, value)
                }
            }

            // 2. MODIFICATION: Zip the new contextual flows.
            val initialFlow = contextualFlows.first().map { listOf(it) }
            val zippedFlow = contextualFlows.drop(1).fold(initialFlow) { acc, nextFlow ->
                acc.zip(nextFlow) { list, newItemPair -> list + newItemPair }
            }

            Util.log("EventsCollector - All flows[${currentFlowHolders.size}] are zipped")
            Util.log("EventsCollector started listening for events...")
            var collectedTimes = 0
            zippedFlow.collect { resultPairs -> // This is now a List<Pair<KProperty, Any>>
                collectedTimes++

                // 3. MODIFICATION: Build the argument map and call the constructor by name.
                val constructor = classType.primaryConstructor!!
                val argumentsMap = resultPairs.associate { (property, value) ->
                    val parameter = constructor.parameters.first { it.name == property.name }
                    parameter to value
                }
                val finalObject = constructor.callBy(argumentsMap)
                Util.logw("EventsCollector collected data[$finalObject]")
                onResult.invoke(finalObject, null)

                if (collectionCount != null && collectedTimes >= collectionCount) {
                    Util.log("EventsCollector collected maximum of $collectionCount times, stopping collection.")
                    this@EventsCollector.cancel()
                }
            }
        }
    }

    /**
     * Emits a value for a specific property of the target data class `T`.
     *
     * This function is thread-safe and routes the provided value to the correct
     * internal flow corresponding to the given property. The collector will only
     * produce a result once a value has been emitted for every property.
     *
     * @param P The type of the property and the value being emitted.
     * @param property A KProperty1 reference to the target property (e.g., `UserData::name`).
     * @param value The value to emit. Its type must match the property's type.
     */
    fun <P> emit(property: KProperty1<T, P>, value: P) {
        //TODO we also need to check type of field in runtime to worn user of type mismatch like field is int and user gave us string, we need compilation error
        emitterJob = scope?.launch {
            Util.log("EventsCollector - Emitting value for property: ${property.name}[${property.returnType}] with value: $value")
            val handler = propertyHandlers?.find { it.property == property }
            handler?.flow?.emit(value as Any)
        }
    }

    /**
     * Logs the initial configuration of the EventsCollector upon its creation.
     *
     * This is a private helper function used for debugging and diagnostics. It provides a
     * formatted, multiline summary of the essential parameters the collector
     * is working with, including:
     * - The simple name of the target data class.
     * - A list of all properties to be collected.
     * - The configured collection count.
     * - The CoroutineDispatcher being used for its internal scope.
     */
    //TODO we can improve logs to be a table kind
    private fun logInitialization() {
        val properties = classType.declaredMemberProperties
            .joinToString(separator = "\n") {
                "    - ${it.name}: ${it.returnType}"
            }

        val logMessage = """
        ||----------- EventsCollector Initialized ----------------
        || Target Class: ${classType.simpleName}
        || Properties to collect:
        |$properties
        || Collection Count: [${collectionCount ?: "unlimited"}]
        || Dispatcher: $dispatcher
        ||---------------------------------------------------------
    """.trimMargin()

        Util.logw(logMessage)
    }

    /**
     * Immediately cancels all ongoing collection jobs and releases all internal resources,
     * including flows, properties, and coroutine scopes.
     *
     * After calling this method, the `EventsCollector` instance should be considered
     * inactive and should not be used again. All internal references are nullified
     * to prevent memory leaks.
     */
    fun cancel() {
        emitterJob?.cancel()
        emitterJob = null
        collectorJob?.cancel()
        collectorJob = null
        scope?.cancel()
        scope = null
        propertyHandlers?.forEach {
            it.flow = null
            it.property = null
        }
        propertyHandlers?.clear()
        propertyHandlers = null
        Util.log("EventsCollector cancelled and all resources released.")
    }

}
