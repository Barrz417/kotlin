// RUN_PIPELINE_TILL: FRONTEND
class C {
    val <!SYNTAX!>(x, y)<!> = <!UNRESOLVED_REFERENCE!>Pair<!>(1, 2)
}

/* GENERATED_FIR_TAGS: classDeclaration, integerLiteral */
