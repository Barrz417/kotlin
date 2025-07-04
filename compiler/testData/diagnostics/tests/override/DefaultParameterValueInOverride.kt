// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
open class A {
    open fun foo(a : Int) {}
}

class C : A() {
    override fun foo(a : Int = <!DEFAULT_VALUE_NOT_ALLOWED_IN_OVERRIDE!>1<!>) {
    }
}

class D : A() {
    override fun foo(a : Int = <!DEFAULT_VALUE_NOT_ALLOWED_IN_OVERRIDE!>1<!>) {
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, override */
