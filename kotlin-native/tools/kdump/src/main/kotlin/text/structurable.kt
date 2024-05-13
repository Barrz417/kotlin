package text

class Structurable(
        private val appendable: Appendable,
        private val firstCharPrefix: String = "",
        internal var isFirstChar: Boolean = true) : CharAppendable {
  override fun append(char: Char): Appendable = also {
    if (isFirstChar) {
      appendable.append(firstCharPrefix)
      isFirstChar = false
    }
    appendable.append(char)
  }

  fun struct(name: String, fn: Structurable.() -> Unit) {
    append(name)
    appendIndented { apply { Structurable(this, "\n").fn() } }
    isFirstChar = true
  }

  fun field(name: String, fn: Structurable.() -> Unit) {
    append(name)
    Structurable(this, ": ").fn()
    isFirstChar = true
  }

  fun line(fn: Structurable.() -> Unit) {
    Structurable(this, "").fn()
    isFirstChar = true
  }

  fun value(any: Any?) {
    append(any.toString())
  }
}

fun Appendable.appendStructurable(fn: Structurable.() -> Unit) = apply {
  Structurable(this, "").fn()
}

fun structurableString(fn: Structurable.() -> Unit) = run {
  StringBuilder().apply {
    appendStructurable { fn() }
  }.toString()
}
