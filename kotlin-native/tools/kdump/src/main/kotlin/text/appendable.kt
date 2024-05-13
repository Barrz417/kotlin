package text

fun appendableString(fn: Appendable.() -> Appendable) = StringBuilder().apply { fn() }.toString()

object PrintAppendable: Appendable {
  override fun append(csq: CharSequence) = apply {
    print(csq)
  }

  override fun append(csq: CharSequence, start: Int, end: Int) = apply {
    print(csq.subSequence(start, end))
  }

  override fun append(c: Char) = apply {
    print(c)
  }
}

interface CharAppendable: Appendable {
  override fun append(csq: CharSequence) = apply {
    csq.forEach { append(it) }
  }

  override fun append(csq: CharSequence, start: Int, end: Int) = apply {
    append(csq.subSequence(start, end))
  }
}

fun Appendable.appendNonISOControl(fn: Appendable.() -> Appendable) = also { appendable ->
  object : CharAppendable {
    override fun append(char: Char) = apply {
      if (char.isISOControl()) {
        appendable.append(".")
      } else {
        appendable.append(char)
      }
    }
  }.fn()
}

fun Appendable.appendIndented(fn: Appendable.() -> Appendable) = also { appendable ->
  object : CharAppendable {
    var isIndentStart = false

    override fun append(char: Char) = apply {
      if (char == '\n') {
        isIndentStart = true
      } else if (isIndentStart) {
        appendable.append("  ")
        isIndentStart = false
      }
      appendable.append(char)
    }
  }.fn()
}

fun Appendable.appendPadded(size: Int, fn: Appendable.() -> Appendable) = also { appendable ->
  object : CharAppendable {
    var remaining = size

    override fun append(char: Char) = apply {
      if (remaining > 0) {
        appendable.append(char)
        remaining--
      }
    }

    fun pad() {
      repeat(remaining) {
        appendable.append(' ')
      }
    }
  }.apply { fn() }.pad()
}

class StructAppendable(
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

  fun appendStruct(name: String, fn: StructAppendable.() -> Unit) {
    append(name)
    appendIndented { apply { StructAppendable(this, "\n").fn() } }
    isFirstChar = true
  }

  fun appendField(name: String, fn: StructAppendable.() -> Unit) {
    append(name)
    StructAppendable(this, ": ").fn()
    isFirstChar = true
  }

  fun appendItem(fn: StructAppendable.() -> Unit) {
    StructAppendable(this, "").fn()
    isFirstChar = true
  }
}

fun Appendable.appendStruct(fn: StructAppendable.() -> Unit) = apply {
  StructAppendable(this, "").fn()
}

fun structString(fn: StructAppendable.() -> Unit) = run {
  StringBuilder().apply {
    appendStruct { fn() }
  }.toString()
}
