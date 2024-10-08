package foo

// CHECK_LABELS_COUNT: function=test name=loop count=1
// CHECK_LABELS_COUNT: function=test name=loop_0 count=1

fun test() {
    var i = 0
    var j = 0

    loop@ for (m in 1..10) {
        if (m == 4) break@loop
        j = m
    }

    loop@ for (k in 1..10) {
        if (k == 4) break@loop
        i = k
    }

    assertEquals(3, j)
    assertEquals(3, i)
}

fun box(): String {
    test()

    return "OK"
}