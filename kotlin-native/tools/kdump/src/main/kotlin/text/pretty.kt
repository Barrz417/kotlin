package text

@DslMarker
annotation class PrettyDslMarker

@PrettyDslMarker
class Pretty(appendable: Appendable, private val firstCharPrefix: String) {
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

fun Appendable.pretty(fn: Pretty.() -> Unit) = apply {
  Pretty(this, "").fn()
}

fun prettyString(fn: Pretty.() -> Unit) = run {
  StringBuilder().apply { pretty { fn() } }.toString()
}
