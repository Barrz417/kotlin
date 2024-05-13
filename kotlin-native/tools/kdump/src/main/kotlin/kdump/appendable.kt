package kdump

import base.Endianness
import text.StructAppendable

fun Appendable.appendLiteral(string: String) = apply {
  append('"')
  append(string)
  append('"')
}

fun Appendable.appendNumber(int: Int) = apply {
  append(int.toString())
}

fun Appendable.appendId(long: Long) = apply {
  append(long.toString())
}

fun Appendable.appendBytes(byteArray: ByteArray) {
  append("TODO")
}

fun StructAppendable.appendName(enum: Enum<*>) = apply {
  append(enum.name)
}

fun StructAppendable.append(dump: Dump) {
  appendStruct("dump") {
    appendHeader(dump)
    append(dump.endianness)
    append(dump.idSize)
    appendStruct("items") {
      dump.items.forEach { append(it) }
    }
  }
}

fun StructAppendable.appendHeader(dump: Dump) {
  appendField("header") {
    appendLiteral(dump.headerString)
  }
}

fun StructAppendable.append(endianness: Endianness) {
  appendField("endianness") {
    appendName(endianness)
  }
}

fun StructAppendable.append(idSize: IdSize) {
  appendField("id size") {
    append(idSize.byteCount.toString())
  }
}

fun StructAppendable.append(item: Item) {
  when (item) {
    is Type ->
      appendStruct("type") {
        appendField("id") { appendId(item.id) }
        appendField("super type id") { appendId(item.superTypeId) }
        appendField("package name") { appendLiteral(item.packageName) }
        appendField("relative name") { appendLiteral(item.relativeName) }
        appendField("body") { append(item.body) }
      }
    is ObjectItem ->
      appendStruct("object") {
        appendField("id") { appendId(item.id) }
        appendField("type id") { appendId(item.typeId) }
        appendField("byte array") { appendBytes(item.byteArray) }
      }
    is ArrayItem ->
      appendStruct("array") {
        appendField("id") { appendId(item.id) }
        appendField("type id") { appendId(item.typeId) }
        appendField("count") { appendNumber(item.count) }
        appendField("byte array") { appendBytes(item.byteArray) }
      }
    is ExtraObject ->
      appendStruct("extra object") {
        appendField("id") { appendId(item.id) }
        appendField("type id") { appendId(item.baseObjectId) }
        appendField("byte array") { appendId(item.associatedObjectId) }
      }
    is GlobalRoot ->
      appendStruct("global root") {
        appendField("source") { appendName(item.source) }
        appendField("object id") { appendId(item.objectId) }
      }
    is Thread ->
    appendStruct("thread") {
      appendField("id") { appendId(item.id) }
    }
    is ThreadRoot ->
      appendStruct("thread root") {
        appendField("thread id") { appendId(item.threadId) }
        appendField("source") { appendName(item.source) }
        appendField("object id") { appendId(item.objectId) }
      }
  }
}

fun StructAppendable.append(body: Type.Body) {
  when (body) {
    is Type.Body.Array -> appendStruct("array") {
      appendField("element size") { appendNumber(body.elementSize) }
      body.extra?.let { append(it) }
    }
    is Type.Body.Object -> appendStruct("object") {
      appendField("instance size") { appendNumber(body.instanceSize) }
      body.extra?.let { append(it) }
    }
  }
}

fun StructAppendable.append(extra: Type.Body.Object.Extra) {
  appendStruct("extra") {
    appendStruct("fields") {
      extra.fields.forEach { append(it) }
    }
  }
}

fun StructAppendable.append(extra: Type.Body.Array.Extra) {
  appendStruct("extra") {
    appendField("element type") { appendName(extra.elementType) }
  }
}

fun StructAppendable.append(field: Field) {
  appendStruct("field") {
    appendField("offset") { appendNumber(field.offset) }
    appendField("type") { appendName(field.type) }
    appendField("name") { appendLiteral(field.name) }
  }
}
