// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-74809
// WITH_STDLIB
// LANGUAGE: -UnnamedLocalVariables, +NameBasedDestructuring

fun writeTo(): Boolean = false

fun foo() {
    val <!UNSUPPORTED_FEATURE!>_<!> = writeTo()
    val (a, _) = 1 to 2
    val (_) = 'a' to 'b'

    (val f = first, val _ = second) = "first" to "second"

    when(val <!UNSUPPORTED_FEATURE!>_<!> = writeTo()) {
        true -> {}
        false -> {}
    }

    for (<!UNSUPPORTED_FEATURE!>_<!> in 1..10) {}

    val <!UNSUPPORTED_FEATURE!>_<!> = object {
        val <!UNDERSCORE_IS_RESERVED!>_<!> = <!UNRESOLVED_REFERENCE!>call<!>()
    }

    val <!UNSUPPORTED_FEATURE!>_<!> <!UNNAMED_DELEGATED_PROPERTY!>by<!> lazy { 10 }
    <!UNNAMED_VAR_PROPERTY!>var<!> <!UNSUPPORTED_FEATURE!>_<!> = writeTo()

    <!MUST_BE_INITIALIZED!>val <!UNSUPPORTED_FEATURE, VARIABLE_WITH_NO_TYPE_NO_INITIALIZER!>_<!><!>
    <!MUST_BE_INITIALIZED!>val <!UNSUPPORTED_FEATURE!>_<!>: Int<!>
    val <!UNSUPPORTED_FEATURE!>_<!>: String = <!INITIALIZER_TYPE_MISMATCH!>1<!>
    val <!UNSUPPORTED_FEATURE!>_<!> = 1
    val <!UNSUPPORTED_FEATURE!>_<!>: Int = 1
}

class Foo() {
    val <!UNDERSCORE_IS_RESERVED!>_<!> = <!UNRESOLVED_REFERENCE!>initMe<!>()
}

class Foo2() {
    init {
        val <!UNSUPPORTED_FEATURE!>_<!> = <!UNRESOLVED_REFERENCE!>initMe<!>()
    }
}

val <!UNDERSCORE_IS_RESERVED!>_<!> = writeTo()

val Int.<!UNDERSCORE_IS_RESERVED!>_<!>: String
    get() = this.toString()

val <T> T.<!UNDERSCORE_IS_RESERVED!>_<!>: String
    get() = this.toString()

/* GENERATED_FIR_TAGS: anonymousObjectExpression, classDeclaration, destructuringDeclaration, equalityExpression,
forLoop, functionDeclaration, getter, init, integerLiteral, lambdaLiteral, localProperty, nullableType,
primaryConstructor, propertyDeclaration, propertyDelegate, propertyWithExtensionReceiver, rangeExpression, smartcast,
thisExpression, typeParameter, unnamedLocalVariable, whenExpression, whenWithSubject */
