// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// MODULE: m1-common
// FILE: common.kt

expect sealed class Presence {
    object Online: Presence
    object Offline: Presence
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual typealias Presence = P
sealed class P {
    object Online : P()
    object Offline : P()
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, nestedClass, objectDeclaration, sealed, typeAliasDeclaration */
