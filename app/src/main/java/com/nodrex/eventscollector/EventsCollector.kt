package com.nodrex.eventscollector

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
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
 * This version of the collector uses a `zip`-based approach and is best suited for
 * **sequential workflows**.
 *
 * ### Recommended Usage
 * For the current version, the recommended use case is for a **single collection cycle**.
 * This functionality is guaranteed to work reliably. After a single collection completes
 * (or fails), the instance is cancelled and its resources are released. To collect another
 * set of events, you must create a **new instance** of the `EventsCollector`.
 *
 * ### Lifecycle and Concurrency
 * This class manages its own `CoroutineScope`. A new `Job()` is added to the scope's
 * context (`dispatcher + Job() + exceptionHandler`) to create a self-contained, cancellable
 * lifecycle.
 *
 * This version is "fail-fast": any error during an `emit` will cancel the entire
 * collector. The user is responsible for creating a new instance to try again.
 * Data emitted before a restart will be lost.
 *
 * @param T The type of the data class to be collected and constructed. Must have a primary constructor.
 * @param classType data class `T` used as a template to determine the required properties and their types via reflection.
 * @param onResult A callback lambda that is invoked with the result. It receives a populated instance of `T` on success, or a `Throwable` on failure.
 * @param collectionCount The number of times to collect a complete set of events. `null` (the default) means it will collect continuously until `cancel()` is called.
 * @param dispatcher The `CoroutineDispatcher` on which the collection and result callback will be executed. Defaults to `Dispatchers.Default`.
 */
class EventsCollector<T : Any> @PublishedApi internal constructor(
    private val classType: KClass<T>,
    private val onResult: (result: T?, error: Throwable?) -> Unit,
    private val collectionCount: Int? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    //TODO Phase 2 -> consider scope as well so it will be dismissed automatically when that scope will be canceled, for exmaple viewmodelscope or lifecyclescope
) {

    companion object {
        /**
         * Creates and starts an [EventsCollector] for continuous or a specific number of collections.
         *
         * This factory function provides a clean, type-safe API for creating a collector. Thanks to
         * `inline` and `reified`, you can specify the target data class `T` as a
         * generic parameter (e.g., `start<EventsData>(...)`) without needing to pass the
         * class reference (`EventsData::class`) manually. This is the recommended way to create an instance.
         *
         * @param T The data class type to be collected and instantiated. It must be `reified`.
         * @param onResult The callback lambda that will be invoked for each successful collection or error.
         * @param collectionCount The number of times to collect. Defaults to `null` for continuous collection.
         * @param dispatcher The `CoroutineDispatcher` for the internal scope. Defaults to `Dispatchers.Default`.
         * @return A new, active instance of [EventsCollector].
         */
        inline fun <reified T : Any> start(
            noinline onResult: (result: T?, error: Throwable?) -> Unit,
            collectionCount: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ) = EventsCollector(T::class, onResult, collectionCount, dispatcher)

        /**
         * Creates and starts an [EventsCollector] that collects exactly one time.
         *
         * This is a convenience factory function for the most common use case of collecting a single
         * set of asynchronous data.
         *
         * @param T The data class type to be collected and instantiated. It must be `reified`.
         * @param onResult The callback lambda that will be invoked once with the result or an error.
         * @param dispatcher The `CoroutineDispatcher` for the internal scope. Defaults to `Dispatchers.Default`.
         * @return A new, active instance of [EventsCollector] that will automatically cancel after one collection.
         */
        inline fun <reified T : Any> startSingleCollector(
            noinline onResult: (result: T?, error: Throwable?) -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ) = EventsCollector(T::class, onResult, 1, dispatcher)
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
        Util.loge(
            "Exception[${throwable.message}]\n${
                throwable.stackTrace.joinToString(
                    "\n"
                )
            }"
        )
        onResult(null, throwable)
        cancel()
    }

    /**
     * ### Lifecycle and Concurrency
     * This class manages its own `CoroutineScope`. A new `Job()` is added to the scope's
     * context (`dispatcher + SupervisorJob() + exceptionHandler`) to create a self-contained, cancellable
     * lifecycle. This is the cornerstone of **structured concurrency**. It ensures that when `cancel()`
     * is called on this instance, the parent `Job` is cancelled, which in turn reliably stops all
     * child coroutines launched within this scope (including the main collector and all `emit` jobs)
     * without affecting any external scope.
     */
    private var scope: CoroutineScope? =
        CoroutineScope(dispatcher + Job() + exceptionHandler)
    private var propertyHandlers: MutableList<PropertyHandler<T>>? = null
    private var collectorJob: Job? = null
    private var emitterJob: Job? = null

    /**
     * Private helper class to link a reflected property to its corresponding SharedFlow.
     */
    private class PropertyHandler<T : Any>(var property: KProperty1<T, *>?) {
        var flow: MutableSharedFlow<Any>? = MutableSharedFlow(replay = 1)
    }

    init {
        //require(dataClassInstance::class.isData) { "Only data classes are supported." } //TODO need to implement
        logInitialization()

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
                IllegalStateException("Collector was initialized with invalid properties!")
            )
            cancel()
            return
        }

        collectorJob = scope?.launch {
            val contextualFlows = currentFlowHolders.map { handler ->
                handler.flow!!.map { value ->
                    Pair(handler.property!!, value)
                }
            }

            val initialFlow = contextualFlows.first().map { listOf(it) }
            val zippedFlow = contextualFlows.drop(1).fold(initialFlow) { acc, nextFlow ->
                acc.zip(nextFlow) { list, newItemPair -> list + newItemPair }
            }

            Util.log("All flows[${currentFlowHolders.size}] are zipped! Started listening for events...")
            var collectedTimes = 0
            zippedFlow.collect { resultPairs ->
                collectedTimes++

                val constructor = classType.primaryConstructor!!
                val argumentsMap = resultPairs.associate { (property, value) ->
                    val parameter = constructor.parameters.first { it.name == property.name }
                    parameter to value
                }
                val finalObject = constructor.callBy(argumentsMap)
                Util.logw("Collected data[$finalObject]")
                onResult.invoke(finalObject, null)

                if (collectionCount != null && collectedTimes >= collectionCount) {
                    Util.logw("Maximum number[$collectionCount] of collection was reached, canceling collector.")
                    this@EventsCollector.cancel()
                }
            }
        }
    }

    /**
     * Emits a value for a specific property of the target data class `T`.
     *
     * This function is thread-safe. For each collection cycle, the internal `zip` operator will wait
     * for the first available event for each property.
     *
     * **⚠️ Important Note on Concurrency:** This collector is designed for sequential workflows where the
     * next set of data is fetched only after a result is received. If you emit multiple values for
     * one property before other properties have been emitted for the current collection cycle, the
     * internal `SharedFlow` (with `replay = 1`) will only hold the **latest** value. This can lead to
     * "mixed data" results where the latest value for one property is paired with an earlier value
     * of another. A future release will introduce a `BatchingEventsCollector` to handle these
     * advanced concurrent cases safely.
     *
     * @param P The type of the property and the value being emitted.
     * @param property A KProperty1 reference to the target property (e.g., `UserData::name`).
     * @param value The value to emit. Its type must match the property's type.
     */
    fun <P> emit(property: KProperty1<T, P>, value: P) {
        emitterJob = scope?.launch {
            ensureActive()

            // Runtime check to prevent type mismatches that bypass compile-time checks.
            val expectedType = property.returnType
            if (value != null && value::class != expectedType.classifier) {
                throw IllegalArgumentException("Type mismatch for property: ${property.name} -> " + "Expected type <$expectedType> but got value [$value] of type (${value::class.simpleName})")
            }

            Util.log("Emitting value for property: ${property.name}[$expectedType] with value: $value")
            val handler = propertyHandlers?.find { it.property == property }
            handler?.flow?.emit(value as Any)
        }
    }

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
     * Immediately cancels all ongoing collection jobs and releases all internal resources.
     *
     * This method is idempotent and can be called multiple times. After calling, the
     * `EventsCollector` instance should be considered inactive and should not be used again.
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
        Util.log("Cancelled and all resources released.")
    }

}
