fun box(): String {
    var log = ""

    var s: Any? = null
    for (t in arrayOf("1", "2", "3")) {
        class C() {
            val y = t

            inner class D() {
                fun copyOuter() = C()
            }
        }

        if (s == null) {
            s = C()
        }

        val c = (s as C).D().copyOuter()
        log += c.y
    }

    if (log != "111") return "fail: ${log}"

    return "OK"
}
