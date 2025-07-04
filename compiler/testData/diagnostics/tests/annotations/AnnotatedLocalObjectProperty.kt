// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
annotation class My

fun foo(): Int {
    val s = object {
        @My val bar: Int = 0
    }
    return s.bar
}

/* GENERATED_FIR_TAGS: annotationDeclaration, anonymousObjectExpression, functionDeclaration, integerLiteral,
localProperty, propertyDeclaration */
