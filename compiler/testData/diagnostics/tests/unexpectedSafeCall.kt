// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE
// ISSUE: KT-59860

fun test() {
    val b: Int
    run { b = 1 }<!UNNECESSARY_SAFE_CALL!>?.<!>let {} // K1: UNNECESSARY_SAFE_CALL, K2: UNEXPECTED_SAFE_CALL
}

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, integerLiteral, lambdaLiteral, localProperty, nullableType,
propertyDeclaration, safeCall */
