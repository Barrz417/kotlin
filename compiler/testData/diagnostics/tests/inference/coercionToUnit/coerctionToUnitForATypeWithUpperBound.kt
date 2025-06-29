// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER

interface Base
class Foo : Base

fun <T : Base> myRun(action: () -> T): T = action()

fun foo(f: (Foo) -> Unit) {}

fun main() {
    foo { f: Foo ->
        myRun { f }
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, functionalType, interfaceDeclaration, lambdaLiteral,
typeConstraint, typeParameter */
