// KT-72862: No function found for symbol
// IGNORE_NATIVE: cacheMode=STATIC_USE_HEADERS_EVERYWHERE
// MODULE: lib
// FILE: a.kt
private fun String.privateExtension() = "${this}K"
internal inline fun String.internalInlineMethod() = privateExtension()

// MODULE: main()(lib)
// FILE: main.kt
fun box(): String {
    return "O".internalInlineMethod()
}
