// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
fun <T> Array<T>.foo() {}

fun test(array: Array<out Int>) {
    array.foo()
    array.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>foo<!><<!PROJECTION_ON_NON_CLASS_TYPE_ARGUMENT!>out<!> Int>()
}

/* GENERATED_FIR_TAGS: capturedType, funWithExtensionReceiver, functionDeclaration, nullableType, outProjection,
typeParameter */
