package org.jetbrains.kotlin.backend.konan.tests

import org.jetbrains.kotlin.backend.konan.objcexport.getClassIfCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class GetClassIfCategoryTest : ObjCExportTest() {

    @Test
    fun `test - null when there is receiver`() {
        createModule(
            """
            class Bar
            class Foo {
                fun memberFoo() = Unit
                fun Bar.memberBarExtension() = Unit
                fun String.memberStringExtension() = Unit
                val prop = 42
            }
        """.trimMargin()
        )
        val fooClass = getClass("Foo")

        assertNull(getClassIfCategory(fooClass.getMemberFun("memberFoo")))
        assertNull(getClassIfCategory(fooClass.getMemberFun("memberBarExtension")))
        assertNull(getClassIfCategory(fooClass.getMemberFun("memberStringExtension")))
        assertNull(getClassIfCategory(fooClass.getMemberProperty("prop")))
    }

    @Test
    fun `test - null when there is no extension and no receiver`() {
        createModule(
            """
            fun topLevelFoo() = Unit
            val prop = 42
        """.trimMargin()
        )
        assertNull(getClassIfCategory(getTopLevelFun("topLevelFoo")))
        assertNull(getClassIfCategory(getTopLevelProp("prop")))
    }

    @Test
    fun `test - null when extension isObjCObjectType == true`() {
        createModule(
            """
            import kotlinx.cinterop.ObjCObject
            fun ObjCObject.foo() = Unit
        """.trimMargin()
        )
        assertNull(getClassIfCategory(getTopLevelFun("foo")))
    }

    @Test
    fun `test - null when extension is interface`() {
        createModule(
            """
            interface Foo
            fun Foo.foo() = Unit
        """.trimMargin()
        )
        assertNull(getClassIfCategory(getTopLevelFun("foo")))
    }

    @Test
    fun `test - null when extension type is inlined`() {
        createModule(
            """
            @JvmInline
            value class Foo(val i: Int)
            fun Foo.foo() = Unit
        """.trimMargin()
        )
        assertNull(getClassIfCategory(getTopLevelFun("foo")))
    }

    @Test
    fun `test - null when extension type isSpecialMapped == true`() {
        createModule(
            """
            fun Any.anyFoo() = Unit
            fun List<String>.listFoo() = Unit
            fun String.stringFoo() = Unit
            fun Function<String>.fooFun() = Unit
        """.trimMargin()
        )
        assertNull(getClassIfCategory(getTopLevelFun("anyFoo")))
        assertNull(getClassIfCategory(getTopLevelFun("listFoo")))
        assertNull(getClassIfCategory(getTopLevelFun("stringFoo")))
        assertNull(getClassIfCategory(getTopLevelFun("fooFun")))
    }
}

