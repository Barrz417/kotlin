// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_VARIABLE

fun foo() {
    val f = myRun<Unit> {
        123
    }
}

fun <R> myRun(block: () -> R): R = block()

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, integerLiteral, lambdaLiteral, localProperty, nullableType,
propertyDeclaration, typeParameter */
