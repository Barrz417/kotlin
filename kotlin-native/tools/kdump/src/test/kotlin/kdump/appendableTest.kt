package kdump

import base.Endianness
import kotlin.test.Test
import kotlin.test.assertEquals
import text.structString

class StructuralAppendableTest {
  @Test
  fun str() {
//    assertEquals("""dump
//  header: "Foo"
//  endianness: big
//  id size: 2""".trimIndent(), structString { append(Dump("Foo", Endianness.BIG_ENDIAN, IdSize.BITS_16, listOf())) })
  }
}