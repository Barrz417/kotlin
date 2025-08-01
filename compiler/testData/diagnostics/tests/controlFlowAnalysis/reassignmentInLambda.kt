// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// ISSUE: KT-61794

private fun createStubFunction(expression: String?): String? {
    val tmp = expression?.let {
        it
    } ?: return null
    return tmp
}

/* GENERATED_FIR_TAGS: elvisExpression, functionDeclaration, lambdaLiteral, localProperty, nullableType,
propertyDeclaration, safeCall */
