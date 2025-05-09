// IGNORE_DEXING
// WITH_STDLIB
// LANGUAGE: +InstantiationOfAnnotationClasses

// JVM_ABI_K1_K2_DIFF: K2 serializes annotation parameter default values (KT-59526).

// MODULE: lib
// FILE: lib.kt

package a

import kotlin.reflect.KClass

annotation class Two<K, V>(val s: String)
annotation class Nesting(val a2: Two<String, List<String>> = Two("two"))

annotation class WithKClass<K: Any>(val k: KClass<K>)


// MODULE: app(lib)
// FILE: app.kt

package test

import a.*
import kotlin.test.*

fun box(): String {
    val t = Two<String, Int>("two")
    assertEquals("two", t.s)
    val n = Nesting()
    assertEquals("two", n.a2.s)
    val wk = WithKClass(String::class)
    assertTrue(String::class == wk.k)
    return "OK"
}
