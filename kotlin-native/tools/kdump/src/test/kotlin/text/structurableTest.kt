package text

import kotlin.test.Test
import kotlin.test.assertEquals

class StructurableTest {
  @Test
  fun str() {
    assertEquals("", structurableString {})

    assertEquals("10", structurableString {
      value(10)
    })

    assertEquals("id: 10", structurableString {
      field("id") { value(10) }
    })

    assertEquals("vector\n  x: 10\n  y: 20", structurableString {
      struct("vector") {
        field("x") { value(10) }
        field("y") { value(20) }
      }
    })
  }
}