// RUN_PIPELINE_TILL: FRONTEND
enum class A {
    A(1), B(2), C("test");

    constructor(x: Int) : <!DELEGATION_SUPER_CALL_IN_ENUM_CONSTRUCTOR!>super<!>()
    constructor(t: String) : this(10)
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry, integerLiteral, secondaryConstructor */
