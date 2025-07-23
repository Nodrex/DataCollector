package com.nodrex.datacollector.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class AnnotationDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MissingCollectableAnnotation",
            briefDescription = "Class is missing @CollectableData annotation",
            explanation = """
                Any class used with the DataCollector must be annotated with [@CollectableData].
                This allows the KSP processor to validate data class.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.FATAL,
            implementation = Implementation(AnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        // We want to inspect function calls.
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val resolvedMethod = node.resolve() ?: return

                // Check if the method is one of our factory functions.
                if (resolvedMethod.containingClass?.qualifiedName != LibInfo.DATA_COLLECTOR_COMPANION_PATH ||
                    node.methodName !in listOf(
                        LibInfo.COLLECT_FUNCTION_NAME,
                        LibInfo.COLLECT_SINGLE_FUNCTION_NAME
                    )
                ) {
                    return
                }

                // Get the first generic type argument passed to the function (e.g., the 'UserData' in start<UserData>(...)).
                val typeArgument = node.typeArguments.firstOrNull() ?: return
                val resolvedClass = (typeArgument as? PsiClassType)?.resolve() ?: return

                // Check if the resolved class has our required annotation.
                val hasAnnotation =
                    resolvedClass.hasAnnotation(LibInfo.ANNOTATION_CLASS_PATH)

                if (!hasAnnotation) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "The class [${resolvedClass.name}] must be annotated with [@CollectableData] to be used with DataCollector."
                    )
                }
            }
        }
    }
}