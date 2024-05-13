package text

import kotlin.test.Test
import kotlin.test.assertEquals

class PrettyTest {
  @Test
  fun pretty() {
    assertEquals("", prettyString {})

    assertEquals("10", prettyString {
      plain { append("10") }
    })

    assertEquals("id: 10", prettyString {
      field("id") { plain { append("10") } }
    })

    assertEquals("vector\n  x: 10\n  y: 20", prettyString {
      struct("vector") {
        field("x") { plain { append("10") } }
        field("y") { plain { append("20") } }
      }
    })
  }
}