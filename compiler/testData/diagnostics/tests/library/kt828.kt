// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
fun test() {
    var res : Boolean = true
    res = (res and false)
    res = (res or false)
    res = (res xor false)
    res = (true and false)
    res = (true or false)
    res = (true xor false)
    res = (!true)
    res = (true && false)
    res = (true || false)
}

/* GENERATED_FIR_TAGS: andExpression, assignment, disjunctionExpression, functionDeclaration, localProperty,
propertyDeclaration */
