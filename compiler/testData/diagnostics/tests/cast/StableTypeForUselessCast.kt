// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER

interface I1
interface I2

fun foo(i: I1) {}
fun foo(i: I2) {}

fun bar(i: I1) {
    if (i is I2) {
        foo(i as I1)
        foo(i as I2)
    }
}

/* GENERATED_FIR_TAGS: asExpression, functionDeclaration, ifExpression, interfaceDeclaration, intersectionType,
isExpression, smartcast */
