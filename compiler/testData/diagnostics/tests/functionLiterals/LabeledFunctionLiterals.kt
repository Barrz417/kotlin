// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
package h

//traits to make ambiguity with function literal as an argument
interface A
interface B
interface C: A, B

fun <T> foo(a: A, f: () -> T): T = f()
fun <T> foo(b: B, f: () -> T): T = f()

fun test(c: C) {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>(c) f@ {
        c<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
    }
}

/* GENERATED_FIR_TAGS: checkNotNullCall, functionDeclaration, functionalType, interfaceDeclaration, lambdaLiteral,
nullableType, typeParameter */
