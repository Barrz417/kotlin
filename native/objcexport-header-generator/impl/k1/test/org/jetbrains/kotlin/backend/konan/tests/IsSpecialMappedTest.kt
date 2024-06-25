package org.jetbrains.kotlin.backend.konan.tests

import org.jetbrains.kotlin.backend.konan.objcexport.isSpecialMapped
import org.jetbrains.kotlin.test.util.JUnit4Assertions.assertFalse
import org.jetbrains.kotlin.test.util.JUnit4Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsSpecialMappedTest : ObjCExportTest() {
    @Test
    fun `test - basic class`() {
        createModule("class Foo")
        assertFalse(isSpecialMapped(getClass("Foo")))
    }

    @Test
    fun `test - any type`() {
        createModule("fun Any.anyFoo() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("anyFoo")))
    }

    @Test
    fun `test - list`() {
        createModule("fun List<Any>.listFoo() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("listFoo")))
    }

    @Test
    fun `test - mutable list`() {
        createModule("fun MutableList<Any>.mutableList() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("mutableList")))
    }

    @Test
    fun `test - set`() {
        createModule("fun Set<Any>.set() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("set")))
    }

    @Test
    fun `test - mutable set`() {
        createModule("fun MutableSet<Any>.mutableSet() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("mutableSet")))
    }

    @Test
    fun `test - map`() {
        createModule("fun Map<Any, Any>.map() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("map")))
    }

    @Test
    fun `test - mutable map`() {
        createModule("fun MutableMap<Any, Any>.mutableMap() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("mutableMap")))
    }

    @Test
    fun `test - long`() {
        createModule("fun Long.long() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("long")))
    }

    @Test
    fun `test - string`() {
        createModule("fun String.string() = Unit")
        assertTrue(isSpecialMapped(getTopLevelFunExtensionType("string")))
    }

    @Test
    fun `test - super type`() {
        createModule("abstract class Foo: List<String>")
        assertTrue(isSpecialMapped(getClass("Foo")))
    }
}