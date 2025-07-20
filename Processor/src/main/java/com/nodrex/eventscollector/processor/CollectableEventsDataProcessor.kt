package com.nodrex.eventscollector.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * The provider class that KSP uses to create an instance of your processor.
 */
class CollectableEventsDataProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return CollectableEventsDataProcessor(environment.logger)
    }
}

/**
 * The processor class containing the logic to validate annotated classes.
 */
class CollectableEventsDataProcessor(private val logger: KSPLogger) : SymbolProcessor {
    /**
     * This function is the entry point for the processor.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all classes annotated with your @CollectableEventsData annotation.
        val symbols = resolver.getSymbolsWithAnnotation("com.nodrex.eventscollector.annotations.CollectableEventsData")

        // Go through each class and validate it.
        symbols.filterIsInstance<KSClassDeclaration>().forEach { classDeclaration ->
            validate(classDeclaration)
        }

        // Return an empty list because we are only validating, not generating new code.
        return emptyList()
    }

    /**
     * Contains the specific rules to validate a class declaration.
     */
    private fun validate(classDeclaration: KSClassDeclaration) {
        // Rule 1: Must be a data class.
        if (!classDeclaration.isDataClass()) {
            logger.error("@CollectableEventsData can only be used on data classes.", classDeclaration)
        }
        // Rule 2: Must have a primary constructor.
        if (classDeclaration.primaryConstructor == null) {
            logger.error("@CollectableEventsData classes must have a primary constructor.", classDeclaration)
        }
    }

    private fun KSClassDeclaration.isDataClass(): Boolean {
        return modifiers.contains(Modifier.DATA)
    }

}