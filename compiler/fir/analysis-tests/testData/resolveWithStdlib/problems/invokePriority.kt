// RUN_PIPELINE_TILL: BACKEND
class A {
    fun bar() {
        val foo: String.() -> Unit = {} // (1)
        fun String.foo(): Unit {} // (2)
        "1".foo() // resolves to (2)
        with("2") {
            foo() // resolves to (1)
        }
    }
}
class B {
    val foo: String.() -> Unit = {} // (1)
    fun String.foo(): Unit {} // (2)
    fun bar() {
        "1".foo() // resolves to (2)
        with("2") {
            foo() // resolves to (2)
        }
    }
}

class E {
    object f {
        operator fun invoke() = Unit // (1)
    }
    companion object {
        val f: () -> Unit = {} // (2)
    }
}

fun main() {
    E.f // Resolves to (2) in old FE (Resolves to (1) in FIR)
    E.f() // Resolves to (2) in old FE (Resolves to (1) in FIR)
    E.f.invoke() // Resolves to (1) in old FE and FIR
}

/* GENERATED_FIR_TAGS: classDeclaration, companionObject, funWithExtensionReceiver, functionDeclaration, functionalType,
lambdaLiteral, localFunction, localProperty, nestedClass, objectDeclaration, operator, propertyDeclaration,
stringLiteral, typeWithExtension */
