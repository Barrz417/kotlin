// RUN_PIPELINE_TILL: BACKEND
fun bar(doIt: Int.() -> Int) {
    val i: Int? = 1
    i?.doIt()
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, integerLiteral, localProperty, nullableType,
propertyDeclaration, safeCall, typeWithExtension */
