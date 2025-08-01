
// FILE: 1.kt
package zzz

public class A(val a: Int, val b: Int)

operator inline fun A.component1() = a

operator inline fun A.component2() = b

// FILE: 2.kt

import zzz.*

fun box(): String {
    var (p, l) = A(1, 11)

    return if (p == 1 && l == 11) "OK" else "fail: $p"
}
