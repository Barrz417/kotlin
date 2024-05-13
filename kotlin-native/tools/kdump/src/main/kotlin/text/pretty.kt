package text

class Pretty(
        private val appendable: Appendable,
        private val firstCharPrefix: String = "",
        private var isFirstChar: Boolean = true): CharAppendable {
  override fun append(char: Char): Appendable = also {
    if (isFirstChar) {
      appendable.append(firstCharPrefix)
      isFirstChar = false
    }
    appendable.append(char)
  }

  fun struct(name: String, fn: Pretty.() -> Unit) {
    append(name)
    appendIndented { apply { Pretty(this, "\n").fn() } }
    isFirstChar = true
  }

  fun field(name: String, fn: Pretty.() -> Unit) {
    append(name)
    Pretty(this, ": ").fn()
    isFirstChar = true
  }

  fun line(fn: Appendable.() -> Unit) {
    Pretty(this, "").appendable.fn()
    isFirstChar = true
  }
}

fun Appendable.pretty(fn: Pretty.() -> Unit) = apply {
  Pretty(this).fn()
}

fun prettyString(fn: Pretty.() -> Unit) = run {
  StringBuilder().apply { pretty { fn() } }.toString()
}
