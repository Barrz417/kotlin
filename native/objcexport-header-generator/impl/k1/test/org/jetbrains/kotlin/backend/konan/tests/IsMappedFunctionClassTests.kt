package org.jetbrains.kotlin.backend.konan.tests

import org.jetbrains.kotlin.backend.konan.objcexport.isMappedFunctionClass
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsMappedFunctionClassTests : ObjCExportTest() {
    @Test
    fun `test - isMappedFunctionClass`() {
        createModule(
            """
            fun Function1<Any, Any>.mappedFooTrue() = Unit
            fun Function<Any>.mappedFooFalse() = Unit
        """.trimIndent()
        )

        assertTrue(getTopLevelFunExtensionType("mappedFooTrue").isMappedFunctionClass())
        assertFalse(getTopLevelFunExtensionType("mappedFooFalse").isMappedFunctionClass())
    }
}