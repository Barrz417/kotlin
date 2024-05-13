package kdump

import base.Endianness
import kotlin.math.min
import text.Pretty
import text.appendNonISOControl
import text.appendPadded

fun Pretty.literal(string: String) = plain {
  append('"')
  appendNonISOControl { append(string) }
  append('"')
}

fun Pretty.decimal(int: Int) = plain {
  append(int.toString())
}

fun Pretty.id(long: Long) = plain {
  append(long.toString())
}

fun Pretty.bytes(byteArray: ByteArray) {
  for (segment in byteArray.indices step 16) {
    plain {
      appendPadded(16 * 3 + 3) {
        for (index in segment..<min(segment + 16, byteArray.size)) {
          append(String.format("%02x", byteArray[index].toInt().and(0xff)))
          append(" ")
        }
      }
      appendNonISOControl {
        for (index in segment..<min(segment + 16, byteArray.size)) {
          append(byteArray[index].toInt().and(0xff).toChar())
        }
      }
    }
  }
}

fun Pretty.name(enum: Enum<*>) = plain {
  append(enum.name)
}

fun Pretty.item(dump: Dump) {
  struct("dump") {
    header(dump)
    item(dump.endianness)
    item(dump.idSize)
    struct("items") {
      dump.items.forEach { item(it) }
    }
  }
}

fun Pretty.header(dump: Dump) {
  field("header") {
    literal(dump.headerString)
  }
}

fun Pretty.item(endianness: Endianness) {
  field("endianness") {
    name(endianness)
  }
}

fun Pretty.item(idSize: IdSize) {
  field("id size") {
    decimal(idSize.byteCount)
  }
}

fun Pretty.item(item: Item) {
  when (item) {
    is Type ->
      struct("type") {
        field("id") { id(item.id) }
        field("super type id") { id(item.superTypeId) }
        field("package name") { literal(item.packageName) }
        field("relative name") { literal(item.relativeName) }
        field("body") { item(item.body) }
      }
    is ObjectItem ->
      struct("object") {
        field("id") { id(item.id) }
        field("type id") { id(item.typeId) }
        struct("bytes") { bytes(item.byteArray) }
      }
    is ArrayItem ->
      struct("array") {
        field("id") { id(item.id) }
        field("type id") { id(item.typeId) }
        field("count") { decimal(item.count) }
        struct("bytes") { bytes(item.byteArray) }
      }
    is ExtraObject ->
      struct("extra object") {
        field("id") { id(item.id) }
        field("base object id") { id(item.baseObjectId) }
        field("associated object id") { id(item.associatedObjectId) }
      }
    is GlobalRoot ->
      struct("global root") {
        field("source") { name(item.source) }
        field("object id") { id(item.objectId) }
      }
    is Thread ->
    struct("thread") {
      field("id") { id(item.id) }
    }
    is ThreadRoot ->
      struct("thread root") {
        field("thread id") { id(item.threadId) }
        field("source") { name(item.source) }
        field("object id") { id(item.objectId) }
      }
  }
}

fun Pretty.item(body: Type.Body) {
  when (body) {
    is Type.Body.Array -> struct("array") {
      field("element size") { decimal(body.elementSize) }
      body.extra?.let { item(it) }
    }
    is Type.Body.Object -> struct("object") {
      field("instance size") { decimal(body.instanceSize) }
      body.extra?.let { item(it) }
    }
  }
}

fun Pretty.item(extra: Type.Body.Object.Extra) {
  struct("extra") {
    struct("fields") {
      extra.fields.forEach { item(it) }
    }
  }
}

fun Pretty.item(extra: Type.Body.Array.Extra) {
  struct("extra") {
    field("element type") { name(extra.elementType) }
  }
}

fun Pretty.item(field: Field) {
  struct("field") {
    field("offset") { decimal(field.offset) }
    field("type") { name(field.type) }
    field("name") { literal(field.name) }
  }
}
