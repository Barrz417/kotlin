// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// ISSUE: KT-45033
// DIAGNOSTICS: -UNUSED_PARAMETER

internal class Bar

sealed class Foo(
    internal val x: Bar,
    y: Bar
)

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration, sealed */
