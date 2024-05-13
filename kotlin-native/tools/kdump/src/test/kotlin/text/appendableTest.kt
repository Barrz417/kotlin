package text

import kotlin.test.Test
import kotlin.test.assertEquals

class AppendableTest {
  @Test
  fun appendableString() {
    assertEquals("", appendableString { this })
    assertEquals("foo", appendableString { append("foo") })
  }

  @Test
  fun intentable() {
    assertEquals(
            "",
            appendableString { appendIndented { this } })

    assertEquals(
            "one",
            appendableString { appendIndented { append("one") } })

    assertEquals(
            "one\n  two\n  three",
            appendableString { appendIndented { append("one\ntwo\nthree") } })
  }
}