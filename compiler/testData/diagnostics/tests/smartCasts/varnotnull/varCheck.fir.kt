// RUN_PIPELINE_TILL: BACKEND
fun get(): String? {
    return ""
}

fun foo(): Int {
    var c: String? = get()
    c!!.length
    return c.length // Previous line should make !! unnecessary here.
}

/* GENERATED_FIR_TAGS: checkNotNullCall, functionDeclaration, localProperty, nullableType, propertyDeclaration,
smartcast, stringLiteral */
