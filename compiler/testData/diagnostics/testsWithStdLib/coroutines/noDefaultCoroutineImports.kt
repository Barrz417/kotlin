// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// FILE: 1.kt
fun test(c: <!UNRESOLVED_REFERENCE!>Continuation<!><Unit>) {}

// FILE: 2.kt
import kotlin.coroutines.*

fun test2(c: Continuation<Unit>) {}

/* GENERATED_FIR_TAGS: functionDeclaration */
