// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// MODULE: m1-common
// FILE: common.kt

expect open class Foo {
    fun existingMethod()
    val existingParam: Int
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual open class Foo {
    actual fun existingMethod() {}
    actual val existingParam: Int = 904

    internal fun injectedMethod() {}
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, integerLiteral, propertyDeclaration */
