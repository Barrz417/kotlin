// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// WITH_STDLIB
// ISSUE: KT-58447

fun @ParameterName(<!UNRESOLVED_REFERENCE!>component2<!>) Int.component2() = this + 2

/* GENERATED_FIR_TAGS: additiveExpression, funWithExtensionReceiver, functionDeclaration, integerLiteral, thisExpression */
