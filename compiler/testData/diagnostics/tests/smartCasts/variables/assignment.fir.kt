// RUN_PIPELINE_TILL: FRONTEND
fun foo() {
    var v: Any = 42
    v.<!UNRESOLVED_REFERENCE!>length<!>()
    v = "abc"
    v.length
    v = 42
    v.<!UNRESOLVED_REFERENCE!>length<!>()
    v = "abc"
    v.length
}

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, integerLiteral, localProperty, propertyDeclaration, smartcast,
stringLiteral */
