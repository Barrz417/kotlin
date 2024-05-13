package text

import kotlin.test.Test
import kotlin.test.assertEquals

class PrettyTest {
  @Test
  fun pretty() {
    assertEquals("\n", prettyString {})

    assertEquals("10\n", prettyString {
      plain { append("10") }
    })

    assertEquals("id: 10\n", prettyString {
      field("id") { plain { append("10") } }
    })

    assertEquals("vector\n  x: 10\n  y: 20\n", prettyString {
      struct("vector") {
        field("x") { plain { append("10") } }
        field("y") { plain { append("20") } }
      }
    })
  }
}