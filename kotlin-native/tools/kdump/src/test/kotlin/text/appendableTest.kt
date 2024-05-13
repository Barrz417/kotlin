package text

import kotlin.test.Test
import kotlin.test.assertEquals

class AppendableTest {
  @Test
  fun appendableString() {
    assertEquals("foo", appendableString { append("foo") })
  }

  @Test
  fun appendIndented() {
    assertEquals(
            "one\n  two\n  three",
            appendableString { appendIndented { append("one\ntwo\nthree") } })
  }

  @Test
  fun appendPadded() {
    assertEquals("123  ", appendableString { appendPadded(5) { append("123") } })
    assertEquals("12345", appendableString { appendPadded(5) { append("12345667890") } })
  }

  @Test
  fun appendNonISOControl() {
    assertEquals(
            "one.two.three.",
            appendableString { appendNonISOControl { append("one\ntwo\nthree\u0000") } })
  }

  @Test
  fun structuralAppendable() {
    assertEquals("", prettyString {})

    assertEquals("10", prettyString {
      pretty {
        append("10")
      }
    })

    assertEquals("id: 10", prettyString {
      field("id") { append("10") }
    })

    assertEquals("vector\n  x: 10\n  y: 20", prettyString {
      struct("vector") {
        field("x") { append("10") }
        field("y") { append("20") }
      }
    })
  }
}