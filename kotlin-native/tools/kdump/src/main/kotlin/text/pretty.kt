package text

@DslMarker
annotation class PrettyDslMarker

@PrettyDslMarker
class Pretty private constructor(appendable: Appendable, private val firstCharPrefix: String = "") {
  companion object {
    fun with(appendable: Appendable) = Pretty(appendable)
  }

  private var isFirstChar: Boolean = true

  private val appendable = object : CharAppendable {
    override fun append(char: Char): Appendable = also {
      if (isFirstChar) {
        appendable.append(firstCharPrefix)
        isFirstChar = false
      }
      appendable.append(char)
    }
  }

  fun struct(name: String, fn: Pretty.() -> Unit) {
    appendable.append(name)
    appendable.appendIndented { apply { Pretty(this, "\n").fn() } }
    isFirstChar = true
  }

  fun field(name: String, fn: Pretty.() -> Unit) {
    appendable.append(name)
    Pretty(appendable, ": ").fn()
    isFirstChar = true
  }

  fun plain(fn: Appendable.() -> Unit) {
    Pretty(appendable, "").appendable.fn()
    isFirstChar = true
  }
}

fun Appendable.appendPretty(fn: Pretty.() -> Unit): Appendable = apply {
  Pretty.with(this).fn()
}

fun prettyString(fn: Pretty.() -> Unit) = run {
  StringBuilder().apply { appendPretty { fn() } }.toString()
}

fun prettyPrint(fn: Pretty.() -> Unit) {
  PrintAppendable.appendPretty(fn)
}

fun prettyPrintln(fn: Pretty.() -> Unit) {
  prettyPrint(fn)
  println()
}
