// RUN_PIPELINE_TILL: FRONTEND
fun String.f() {
    <!SUPER_NOT_AVAILABLE!>super@f<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>compareTo<!>("")
    <!SUPER_NOT_AVAILABLE!>super<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>compareTo<!>("")
}

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration, stringLiteral */
