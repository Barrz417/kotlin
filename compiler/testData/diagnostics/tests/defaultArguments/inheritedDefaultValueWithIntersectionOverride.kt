// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// ISSUE: KT-49722

interface I {
    fun h(x: Int = 3, block: () -> String): Any
}

open class A {
    inline fun h(x: Int, block: () -> String) = block()
}

// KT-49722: [NOT_YET_SUPPORTED_IN_INLINE] should be raised for the next line, for intersection override of `h()`, similar to test `inheritedDefaultValue.kt`
class B : A(), I

fun box() = B().h { "OK" }

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, functionalType, inline, integerLiteral,
interfaceDeclaration, lambdaLiteral, stringLiteral */
