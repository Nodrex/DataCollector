package com.nodrex.eventscollector.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class EmitTypeCheckDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "EmitTypeMismatch",
            briefDescription = "Type mismatch in EventsCollector.emit() call",
            explanation = """
                The value passed to the `emit` function must match the type of the property reference.
                This check ensures type safety at build time.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(EmitTypeCheckDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "emit") return
                val resolvedMethod = node.resolve() ?: return
                if (resolvedMethod.containingClass?.qualifiedName != "com.nodrex.eventscollector.EventsCollector") return
                if (node.valueArgumentCount != 2) return

                val propertyArgumentType = node.valueArguments[0].getExpressionType() ?: return
                val valueArgumentType = node.valueArguments[1].getExpressionType() ?: return

                // Cast to a PsiClassType to access its generic parameters.
                if (propertyArgumentType !is PsiClassType) return
                // Get the array of generic types (e.g., [T, P] for KProperty1<T, P>)
                val genericParameters = propertyArgumentType.parameters
                // We need the second parameter (P).
                if (genericParameters.size < 2) return
                val expectedValueType = genericParameters[1]

                if (!expectedValueType.isAssignableFrom(valueArgumentType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Type mismatch. Property expects type [${expectedValueType.presentableText}] but received [${valueArgumentType.presentableText}]"
                    )
                }
            }
        }
    }
}