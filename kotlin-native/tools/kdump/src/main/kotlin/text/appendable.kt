package text

interface CharAppendable: Appendable {
  override fun append(csq: CharSequence) = apply {
    csq.forEach { append(it) }
  }

  override fun append(csq: CharSequence, start: Int, end: Int) = apply {
    append(csq.subSequence(start, end))
  }
}

fun Appendable.appendIndented(fn: Appendable.() -> Appendable) = apply {
  class Intentable(val appendable: Appendable, var isIndentStart: Boolean = false): CharAppendable {
    override fun append(char: Char): Appendable = apply {
      if (char == '\n') {
        isIndentStart = true
      } else if (isIndentStart) {
        appendable.append("  ")
        isIndentStart = false
      }
      appendable.append(char)
    }
  }

  Intentable(this).fn()
}

fun appendableString(fn: Appendable.() -> Appendable) = StringBuilder().apply { fn() }.toString()

