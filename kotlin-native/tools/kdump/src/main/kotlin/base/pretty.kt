package base

data class Pretty(val name: String, val children: List<Any?>) {
  override fun toString() = StringBuilder().apply { append(0, this) }.toString()
}

fun pretty(name: String, vararg children: Any?) =
  Pretty(name, listOf(*children))

fun <T> prettyMap(name: String, children: Iterable<T>, fn: (T) -> Any? = { it }) =
  Pretty(name, children.map(fn))

fun Appendable.appendPretty(indent: Int, any: Any?): Appendable =
  when (any) {
    is Pretty -> append(indent, any)
    is String -> append("\"").append(any).append("\"")
    else -> append(any.toString())
  }

fun Appendable.append(indent: Int, pretty: Pretty): Appendable =
  append(pretty.name).run {
    if (pretty.children.isEmpty()) this
    else pretty.children.singleOrNull().let { single ->
      if (single != null) append(" ").appendPretty(indent, single)
      else pretty.children.fold(this) { appendable, child ->
        appendable
          .append("\n")
          .append("  ".repeat(indent.inc()))
          .appendPretty(indent.inc(), child)
      }
    }
  }
