// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE

// MODULE: if
// FILE: lib.kt
@file:JsExport

@JsName("foo")
public fun foo(k: String): String = "O$k"

// FILE: test.js
function box() {
    return this["if"].foo("K");
}