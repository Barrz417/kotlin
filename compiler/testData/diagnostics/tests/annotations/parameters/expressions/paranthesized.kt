// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
package test

annotation class Ann(<!MISSING_VAL_ON_ANNOTATION_PARAMETER!>i: Int<!>)

@Ann((1 + 2) * 2) class MyClass

// EXPECTED: @Ann(i = 6)

/* GENERATED_FIR_TAGS: annotationDeclaration, classDeclaration, integerLiteral, primaryConstructor */
