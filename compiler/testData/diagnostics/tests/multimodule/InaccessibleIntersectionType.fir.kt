// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-64474, KT-66751
// MODULE: a
// FILE: a.kt
interface A
interface B

// MODULE: b(a)
// FILE: b.kt

class C : A, B
class D : A, B

val c = C()
val d = D()

// MODULE: c(b)
// FILE: c.kt
fun <T> select(vararg t: T): T = t[0]

fun test() {
    val x = <!INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_WARNING!>select<!>(c, d)
    <!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>x<!>
}

/* GENERATED_FIR_TAGS: capturedType, classDeclaration, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, outProjection, propertyDeclaration, typeParameter, vararg */
