// NO_CHECK_LAMBDA_INLINING
// MODULE: lib
// FILE: a.kt
class Foo {
    private fun o() = "O"
    private fun k() = "K"

    internal inline fun internalInlineFun(oo: String = o(), kk: () -> String = { k() }) = oo + kk()
}

// MODULE: main()(lib)
// FILE: main.kt
fun box(): String {
    return Foo().internalInlineFun()
}
