// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +DataObjects
// MODULE: m1-common
// FILE: common.kt
<!INCOMPATIBLE_MODIFIERS, INCOMPATIBLE_MODIFIERS{JVM}!>expect<!> <!INCOMPATIBLE_MODIFIERS, INCOMPATIBLE_MODIFIERS{JVM}!>data<!> object DataObject

// MODULE: m1-jvm()()(m1-common)
// FILE: jvm.kt
actual data object DataObject

/* GENERATED_FIR_TAGS: actual, data, expect, objectDeclaration */
