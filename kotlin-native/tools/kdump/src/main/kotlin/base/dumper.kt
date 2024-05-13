package base

import java.lang.reflect.*

data class Dumper(val indent: Int, val isLine: Boolean, var isEmpty: Boolean = true)

fun withDumper(fn: Dumper.() -> Unit) {
  val dumper = Dumper(indent = 0, isLine = false, isEmpty = true)
  dumper.fn()
  if (!dumper.isEmpty) {
    println()
  }
}

private fun Dumper.dumpItem(fn: Dumper.() -> Unit) {
  if (!isLine) {
    if (!isEmpty) {
      println()
    }
    print("  ".repeat(indent))
  } else {
    if (isEmpty) {
      print(": ")
    } else {
      throw IllegalStateException("More than 1 element in line.")
    }
  }
  isEmpty = false

  fn()
}

fun Dumper.dump(string: String) {
  dumpItem {
    // TODO: Escape
    print("\"")
    print(
      string
      .map { char ->
        when (char) {
          '\n' -> "\\n"
          '\t' -> "\\t"
          '"' -> "\\\""
          else -> if (char.isISOControl()) "\\u${char.code.toString(16)}" else char.toString()
        }
      }
      .joinToString(""))
    print("\"")
  }
}

fun Dumper.dump(number: Number) {
  dumpItem {
    print(number)
  }
}

fun Dumper.dumpAny(any: Any?) {
  dumpItem {
    val string = any.toString()
    print(string.replace("\n", "\\n"))
  }
}

fun Dumper.dump(tag: String, fn: Dumper.() -> Unit) {
  dumpItem {
    print(tag)
    Dumper(indent = indent.inc(), isLine = false, isEmpty = false).fn()
  }
}

fun <T> Dumper.dump(tag: String, items: Iterable<T>, fn: Dumper.(T) -> Unit) {
  dump(tag) {
    items.forEach { fn(it) }
  }
}

fun Dumper.dump(tag: String, any: Any?) {
  dumpItem {
    print(tag)
    print(": ")
    if (any is String) print("\"")
    print(any.toString().replace("\n", "\\n"))
    if (any is String) print("\"")
  }
}

fun Dumper.dump1(tag: String, fn: Dumper.() -> Unit) {
  dumpItem {
    print(tag)
    copy(isLine = true, isEmpty = true).fn()
  }
}

fun Any?.dump() {
  let { withDumper { dumpRef(it) } }
}

fun Dumper.dumpRef(any: Any?) {
  if (any == null) {
    dumpAny(null)
  } else when (any) {
    is Long -> dumpAny("$any (0x${any.toString(16)})")
    is Int -> dumpAny("$any (0x${any.toString(16)})")
    is Short -> dumpAny("$any (0x${any.toString(16)})")
    is Byte -> dumpAny("$any (0x${any.toString(16)})")
    is ByteArray -> dump(any)
    is Number -> dumpAny(any)
    is Boolean -> dumpAny(any)
    is Map<*, *> -> dump(any)
    is String -> dump(any)
    is List<*> -> dump("list", any) { dumpRef(it) }
    is Enum<*> -> dump1(any::class.java.simpleName.nameDumpTag) { dumpAny(any.name) }
    else -> dump(any::class.java.simpleName.nameDumpTag) { dumpFields(any::class.java, any) }
  }
}

val Any?.dumpTagOrNull: String? get() =
  when (this) {
    null -> null
    is ByteArray -> "byte array"
    is Map<*, *> -> "map"
    is List<*> -> "list"
    else -> this::class.java.simpleName.nameDumpTag
  }

fun Dumper.dump(map: Map<*, *>) {
  dump("map") {
    map.entries.forEach { (key, value) ->
      dump1("key") { dumpRef(key) }
      dump1("value") { dumpRef(value) }
    }
  }
}

fun Dumper.dump(byteArray: ByteArray) {
  dump("byte array") {
    var offset = 0
    while (offset < byteArray.size) {
      dump16(byteArray, offset)
      offset += 16
    }
  }
}

fun Dumper.dump16(byteArray: ByteArray, offset: Int) {
  dumpItem {
    repeat(16) { index ->
      val arrayIndex = offset + index
      if (arrayIndex < byteArray.size) {
        val int = byteArray[arrayIndex].toInt().and(0xff)
        print(String.format("%02x", int))
      } else {
        print("  ")
      }
      print(" ")
    }
    print("   ")
    repeat(16) { index ->
      val arrayIndex = offset + index
      if (arrayIndex < byteArray.size) {
        val int = byteArray[arrayIndex].toInt().and(0xff)
        if (int >= 32 && int < 127) {
          print(int.toChar())
        } else {
          print(".")
        }
      }
    }
  }
}

fun Dumper.dumpFields(cls: Class<*>, any: Any) {
  cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
    dump(field, any)
  }
  cls.superclass?.let { superCls ->
    dumpFields(superCls, any)
  }
}

fun Dumper.dump(field: Field, any: Any) {
  try {
    field.isAccessible = true
    val value = field.get(any)
    dumpField(field.name, value)
  } catch (e: IllegalAccessException) {
  } catch (e: InaccessibleObjectException) {
  }
}

fun Dumper.dumpField(name: String, value: Any?) {
  val nameTag = name.nameDumpTag
  val valueTagOrNull = value.dumpTagOrNull
  if (valueTagOrNull == nameTag) {
    dumpRef(value)
  } else if (valueTagOrNull != null && nameTag.endsWith(" $valueTagOrNull")) {
    dump1(nameTag.substring(0, nameTag.length - valueTagOrNull.length - 1)) { dumpRef(value) }
  } else {
    dump1(nameTag) { dumpRef(value) }
  }
}

private val String.nameDumpTag: String get() =
  StringBuilder().also { stringBuilder ->
    var lastChar: Char? = null
    forEach { char ->
      lastChar.let { lastChar ->
        if (char.isUpperCase() && lastChar != null && !lastChar.isUpperCase()) {
          stringBuilder.append(" ")
        }
      }
      stringBuilder.append(char.lowercaseChar())
      lastChar = char
    }
  }.toString()
