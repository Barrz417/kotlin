// RUN_PIPELINE_TILL: BACKEND
// FILE: main.kt
fun <T> myRun(action: () -> T): T = action()

fun test(other: TypeWithBoundedGeneric<*>) = myRun { other }

interface SomeType

abstract class TypeWithBoundedGeneric<T : SomeType>

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, functionalType, interfaceDeclaration, lambdaLiteral,
nullableType, starProjection, typeConstraint, typeParameter */
