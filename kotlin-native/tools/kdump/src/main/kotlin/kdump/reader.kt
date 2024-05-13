package kdump

import base.*
import java.io.*
import io.*

fun InputStream.readEndianness(): Endianness {
  val byte = byteInt()
  return if (byte.and(1) != 0) Endianness.LITTLE_ENDIAN else Endianness.BIG_ENDIAN
}

fun InputStream.readIdSize(): IdSize {
  val size = byteInt()
  return when (size) {
    1 -> IdSize.BITS_8
    2 -> IdSize.BITS_16
    4 -> IdSize.BITS_32
    8 -> IdSize.BITS_64
    else -> throw IOException("Invalid id size: $size.")
  }
}

fun InputStream.readDump(): Dump {
  val headerString = cString().also {
    if (it != "Kotlin/Native dump 1.0.5") {
      throw IOException("invalid header \"$it\"")
    }
  }
  log("header: $headerString")
  val endianness = readEndianness()
  log("endianness: $endianness")
  val idSize = readIdSize()
  log("idSize: $idSize")
  val reader = Reader(this, endianness, idSize)
  val items = reader.readList { readItem() }
  return Dump(
    headerString = headerString,
    endianness = endianness,
    idSize = idSize,
    items = items,
  )
}

data class Reader(
  val inputStream: InputStream,
  val endianness: Endianness,
  val idSize: IdSize,
)

fun Reader.readByte(): Byte =
  inputStream.readByte()

fun Reader.readByteInt(): Int =
  inputStream.readByteInt()

fun Reader.readShort(): Short =
  inputStream.readShort(endianness)

fun Reader.readInt(): Int =
  inputStream.readInt(endianness)

fun Reader.readLong(): Long =
  inputStream.readLong(endianness)

fun Reader.readByteArray(size: Int): ByteArray =
  inputStream.byteArray(size)

fun <T> Reader.readList(fn: Reader.() -> T): List<T> =
  PushbackInputStream(inputStream).list {
    copy(inputStream = this).fn()
  }

fun <T> Reader.readList(size: Int, fn: Reader.() -> T): List<T> =
  inputStream.list(size) {
    copy(inputStream = this).fn()
  }

fun Reader.readString(): String =
  inputStream.cString()

fun Reader.readRootSource(): GlobalRoot.Source =
  readByteInt().let { int ->
    when (int) {
      RootSourceTag.GLOBAL -> GlobalRoot.Source.GLOBAL
      RootSourceTag.STABLE_REF -> GlobalRoot.Source.STABLE_REF
      else -> throw IOException("Unknown root source: $int")
    }
  }

fun Reader.readThreadRootSource(): ThreadRoot.Source =
  readByteInt().let { int ->
    when (int) {
      ThreadRootSourceTag.STACK -> ThreadRoot.Source.STACK
      ThreadRootSourceTag.THREAD_LOCAL -> ThreadRoot.Source.THREAD_LOCAL
      else -> throw IOException("Unknown thread root source: $int")
    }
  }

fun Reader.readItem(): Item =
  when (val tag = readByteInt()) {
    RecordTag.TYPE -> {
      log("TYPE")
      val id = readLong()
      log("  id: $id")
      val flags = readByteInt()
      log("  flags: $flags")
      val isArray = flags.and(0x01) != 0
      val hasExtra = flags.and(0x02) != 0
      val superTypeId = readLong()
      log("  superTypeId: $superTypeId")
      val packageName = readString()
      log("  packageName: $packageName")
      val relativeName = readString()
      log("  relativeName: $relativeName")
      val body =
        if (isArray) {
          val elementSize = readInt()
          log("  elementSize: $elementSize")
          val extra = nullUnless(hasExtra) {
            val elementType = readRuntimeType()
            log("  elementType: $elementType")
            Type.Body.Array.Extra(elementType = elementType)
          }
          Type.Body.Array(
            elementSize = elementSize,
            extra = extra)
        } else {
          val instanceSize = readInt()
          log("  size: $instanceSize")
          val extra = nullUnless(hasExtra) {
            val fields = readList(readInt()) { readField() }
            log("  fields: $fields")
            Type.Body.Object.Extra(fields = fields)
          }
          Type.Body.Object(
            instanceSize = instanceSize,
            extra = extra)
        }
      Type(
        id,
        superTypeId,
        packageName,
        relativeName,
        body)
    }
    RecordTag.OBJECT -> {
      log("OBJECT")
      val id = readLong()
      log("  id: $id")
      val typeId = readLong()
      log("  typeId: $typeId")
      val size = readInt()
      log("  size: $size")
      val byteArray = readByteArray(size)
      ObjectItem(id, typeId, byteArray)
    }
    RecordTag.ARRAY -> {
      log("ARRAY")
      val id = readLong()
      log("  id: $id")
      val typeId = readLong()
      log("  typeId: $typeId")
      val count = readInt()
      log("  count: $count")
      val size = readInt()
      log("  size: $size")
      val byteArray = readByteArray(size)
      ArrayItem(id, typeId, count, byteArray)
    }
    RecordTag.EXTRA_OBJECT -> {
      log("EXTRA_OBJECT")
      val id = readLong()
      log("  id: $id")
      val baseObjectId = readLong()
      log("  baseObjectId: $baseObjectId")
      val associatedObjectId = readLong()
      log("  associatedObjectId: $associatedObjectId")
      ExtraObject(id, baseObjectId, associatedObjectId)
    }
    RecordTag.THREAD -> {
      log("THREAD")
      val id = readLong()
      log("  id: $id")
      Thread(id)
    }
    RecordTag.GLOBAL_ROOT -> {
      log("GLOBAL_ROOT")
      val source = readRootSource()
      log("  source: $source")
      val objectId = readLong()
      log("  objectId: $objectId")
      GlobalRoot(source, objectId)
    }
    RecordTag.THREAD_ROOT -> {
      log("THREAD_ROOT")
      val threadId = readLong()
      log("  threadId: $threadId")
      val source = readThreadRootSource()
      log("  source: $source")
      val objectId = readLong()
      log("  objectId: $objectId")
      ThreadRoot(threadId, source, objectId)
    }

    // Old version of kdump, where ROOT tag = 0x04
    // 0x04 -> {
    //   log("GLOBAL_ROOT")
    //   val objectId = readLong()
    //   log("  objectId: $objectId")
    //   GlobalRoot(GlobalRoot.Source.GLOBAL, objectId)
    // }

    else -> throw IOException("Unknown tag: $tag")
  }

fun Reader.readField(): Field = run {
  val offset = readInt()
  val type = readRuntimeType()
  val name = readString()
  Field(offset, type, name)
}

fun Reader.readRuntimeType(): RuntimeType =
  readByteInt().run {
    runtimeTypeOrNull ?: throw IOException("Invalid runtime type: $this")
  }

val Int.runtimeTypeOrNull: RuntimeType? get() =
  when (this) {
     1 -> RuntimeType.OBJECT
     2 -> RuntimeType.INT_8
     3 -> RuntimeType.INT_16
     4 -> RuntimeType.INT_32
     5 -> RuntimeType.INT_64
     6 -> RuntimeType.FLOAT_32
     7 -> RuntimeType.FLOAT_64
     8 -> RuntimeType.NATIVE_PTR
     9 -> RuntimeType.BOOLEAN
     10 -> RuntimeType.VECTOR_128
     else -> null
  }

@Suppress("UNUSED_PARAMETER")
private fun log(any: Any?) {
  //println(any)
}
