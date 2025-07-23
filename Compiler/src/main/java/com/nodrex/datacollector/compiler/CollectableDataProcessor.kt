package com.nodrex.datacollector.compiler

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * The provider class that KSP uses to create an instance of your processor.
 */
class CollectableDataProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return CollectableDataProcessor(environment.logger)
    }
}

/**
 * The processor class containing the logic to validate annotated classes.
 */
class CollectableDataProcessor(private val logger: KSPLogger) : SymbolProcessor {
    /**
     * This function is the entry point for the processor.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all classes annotated with your @CollectableData annotation.
        val symbols = resolver.getSymbolsWithAnnotation(AnnotationInfo.ANNOTATION_FULL_PATH)

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
            logger.error("@CollectableData can only be used on data classes.", classDeclaration)
        }
        // Rule 2: Must have a primary constructor.
        if (classDeclaration.primaryConstructor == null) {
            logger.error("@CollectableData classes must have a primary constructor.", classDeclaration)
        }
    }

    private fun KSClassDeclaration.isDataClass(): Boolean {
        return modifiers.contains(Modifier.DATA)
    }

}