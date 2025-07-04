// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// ISSUE: KT-41308, KT-47830

fun main() {
    sequence {
        val list: List<String>? = null
        val outputList = <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.List<kotlin.String>")!>list ?: listOf()<!>
        yieldAll(outputList)
    }
}

/* GENERATED_FIR_TAGS: elvisExpression, functionDeclaration, lambdaLiteral, localProperty, nullableType,
propertyDeclaration */
