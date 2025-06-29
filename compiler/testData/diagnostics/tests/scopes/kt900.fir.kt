// RUN_PIPELINE_TILL: FRONTEND
//FILE:a.kt
//KT-900 Inaccessible class should be unresolved

package a

fun foo() {
    val b : <!UNRESOLVED_REFERENCE!>B<!> = <!UNRESOLVED_REFERENCE!>B<!>() //only B() is unresolved, but in ": B" and "B.foo()" B should also be unresolved
    <!UNRESOLVED_REFERENCE!>B<!>.foo()

    <!UNRESOLVED_REFERENCE!>P<!>.foo()

    <!UNRESOLVED_REFERENCE!>M<!>.bar()
}

class A() {
    companion object {
        class B() {
            companion object {
                fun foo() {}
            }
        }

        object P {
            fun foo() {}
        }
    }
}

object N {
    object M {
        fun bar() {}
    }
}

//FILE:b.kt
package b

import b.N.M
import b.A.Companion.P
import b.A.Companion.B

fun foo() {
    val b : B = B()
    B.foo()

    P.foo()

    M.bar()
}

class A() {
    companion object {
        class B() {
            companion object {
                fun foo() {}
            }
        }

        object P {
            fun foo() {}
        }
    }
}

object N {
    object M {
        fun bar() {}
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, companionObject, functionDeclaration, localProperty, nestedClass,
objectDeclaration, primaryConstructor, propertyDeclaration */
