// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
interface A {
    fun foo(): Any
}

interface B {
    fun foo(): String = "A"
}

open class D: B

open <!MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED!>class C<!>: D(), A

// ------------

<!MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED!>class Test<!>: Impl(), CProvider

open class CC

class DD: CC()

interface CProvider {
    fun getC(): CC
}

interface DProvider {
    fun getC(): DD = DD()
}

open class Impl: DProvider

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, interfaceDeclaration, stringLiteral */
