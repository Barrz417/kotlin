import java.io.*
import io.*

fun <T> T.iterate(count: Int, fn: T.() -> T): T =
  if (count == 0) this else fn().iterate(count.dec(), fn)

private enum class IdSize {
  BYTE, SHORT, INT, LONG
}

fun <V> InputStream.readSegments(initial: V, readSegment: InputStream.(V, Byte) -> V): V {
  var value = initial
  while (true) {
    val peek = read()
    if (peek == -1) break
    val tag = peek.toByte()
    @Suppress("UNUSED_VARIABLE")
    val timeDelta = int()
    val length = int()
    println("Tag: $tag")
    println("Length: $length")
    readWithSize(length) { readSegment(value, tag) }
  }
  return value
}

fun <E> HProf.Visitor<E>.parse(inputStream: InputStream): E =
  inputStream.run {
    cString().also {
      if (it != "JAVA PROFILE 1.0.2") {
        throw IOException("invalid header \"$it\"")
      }
    }
    val idSizeInt = int()
    val idSize = when (idSizeInt) {
      1 -> IdSize.BYTE
      2 -> IdSize.SHORT
      4 -> IdSize.INT
      8 -> IdSize.LONG
      else -> throw IOException("Invalid ID size: $idSizeInt")
    }
    val hprofId: InputStream.() -> HProf.Id = {
      HProf.Id(
        when (idSize) {
          IdSize.BYTE -> byte().toInt().and(0xff).toLong()
          IdSize.SHORT -> short().toInt().and(0xFFFF).toLong()
          IdSize.INT -> int().toLong()
          IdSize.LONG -> long()
        })
    }
    val time = long()
    readSegments(header(time)) { body, tag ->
      when (tag.toInt()) {
        0x01 ->
          body.string(hprofId(), string())
        0x02 ->
          body.loadClass(int(), hprofId(), int(), hprofId())
        0x04 ->
          body.stackFrame(hprofId(), hprofId(), hprofId(), hprofId(), int(), int())
        0x05 ->
          body
            .stackTrace(int(), int())
            .iterate(int()) { element(hprofId()) }
            .end()
        0x1C ->
          readHeap(hprofId, body.heap())
        else ->
          body.unknown(tag)
      }
    }.end()
  }

fun <E> InputStream.readHeap(hprofId: InputStream.() -> HProf.Id, initial: HProf.Visitor.Heap<E>): E {
  var heap = initial
  while (true) {
    val peek = read()
    if (peek == -1) break
    val tag = peek.toByte()
    println("Heap tag: $tag")
    heap = when (tag.toInt()) {
      0x01 -> heap.also {
        println("rootJNIGlobal(${hprofId()}, ${hprofId()})")
      }
      0x02 -> heap.also {
        println("rootJNILocal(${hprofId()}, ${int()}, ${int()})")
      }
      0x03 -> heap.also {
        println("rootJavaFrame(${hprofId()}, ${int()}, ${int()})")
      }
      0x05 -> heap.also {
        println("rootStickyClass(${hprofId()})")
      }
      0x08 -> heap.also {
        println("rootThreadObject(${hprofId()}, ${int()}, ${int()})")
      }
      0x20 -> {
        val classObjectId = hprofId()
        val stackTraceSerialNumber = int()
        val superClassObjectId = hprofId()
        val classLoaderObjectId = hprofId()
        val signersObjectId = hprofId()
        val protectionDomainObjectId = hprofId()
        val reservedId1 = hprofId()
        val reservedId2 = hprofId()
        val instanceSize = int()
        print("classDump($instanceSize, ")
        val constantPoolSize = shortInt()
        print("$constantPoolSize, ")
        repeat(constantPoolSize) {
          val constantPoolIndex = short()
          val type = hprofType()
          val value = value(type)
        }
        val numberOfStaticFields = shortInt()
        print("$numberOfStaticFields, ")
        repeat(numberOfStaticFields) {
          val nameStringId = hprofId()
          val type = hprofType()
          val value = value(type)
        }
        val instanceFieldCount = shortInt()
        println("$instanceFieldCount)")
        repeat(instanceFieldCount) {
          val nameStringId = hprofId()
          val type = hprofType()
        }
        heap
      }
      0x21 -> heap.also {
        print("instanceDump(${hprofId()}, ${int()}, ${hprofId()}, ")
        val size = int()
        println("$size)")
        val bytes = byteArray(size)
      }
      0x22 -> heap.also {
        print("objectArrayDump(${hprofId()}, ${int()}, ")
        val size = int()
        print("$size, ")
        val elementTypeId = hprofId()
        println("$elementTypeId)")
        val bytes = byteArray(8 * size)
      }
      0x23 -> heap.also {
        print("primitiveArrayDump(${hprofId()}, ${int()}, ")
        val size = int()
        val type = hprofType()
        val bytes = byteArray(type.size * size)
        println("$type, $size)")
      }
      else ->
        throw IOException("Unknown heap tag ${tag.toInt()}")
    }
  }
  return heap.end()
}

fun InputStream.hprofType(): HProf.Type =
  byte().run {
    hprofType ?: throw IOException("Unknown type byte: $this")
  }

fun InputStream.value(type: HProf.Type): Any =
  when (type) {
    HProf.Type.OBJECT -> long()
    HProf.Type.BYTE -> byte()
    HProf.Type.SHORT -> short()
    HProf.Type.INT -> int()
    HProf.Type.LONG -> long()
    HProf.Type.FLOAT -> float()
    HProf.Type.DOUBLE -> double()
    HProf.Type.BOOLEAN -> boolean()
    HProf.Type.CHAR -> char()
  }

val HProf.Type.size: Int get() =
  when (this) {
    HProf.Type.OBJECT -> 8
    HProf.Type.BYTE -> 1
    HProf.Type.SHORT -> 2
    HProf.Type.INT -> 4
    HProf.Type.LONG -> 8
    HProf.Type.FLOAT -> 4
    HProf.Type.DOUBLE -> 8
    HProf.Type.BOOLEAN -> 1
    HProf.Type.CHAR -> 2
  }

val Byte.hprofType: HProf.Type? get() =
  when (toInt()) {
    2 -> HProf.Type.OBJECT
    4 -> HProf.Type.BOOLEAN
    5 -> HProf.Type.CHAR
    6 -> HProf.Type.FLOAT
    7 -> HProf.Type.DOUBLE
    8 -> HProf.Type.BYTE
    9 -> HProf.Type.SHORT
    10 -> HProf.Type.INT
    11 -> HProf.Type.LONG
    else -> null
  }

object HProf {
  enum class Type {
    OBJECT,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    CHAR,
  }

  data class Id(val long: Long) {
    @ExperimentalStdlibApi
    override fun toString() = "0x${long.toHexString()}"
  }

  interface Visitor<E> {
    fun header(time: Long): Body<E>

    interface Body<E> {
      fun string(
        id: Id,
        string: String,
      ): Body<E> = this

      fun loadClass(
        serialNumber: Int,
        classObjectId: Id,
        stackTraceSerialNumber: Int,
        classNameStringId: Id,
      ): Body<E> = this

      fun heapSummary(
        totalLiveBytes: Int,
        totalLiveInstances: Int,
        totalBytesAllocated: Long,
        totalInstancesAllocated: Long,
      ): Body<E> = this

      fun stackFrame(
        id: Id,
        methodNameStringId: Id,
        methodSignatureStringId: Id,
        fileNameStringId: Id,
        classSerialNumber: Int,
        lineNumber: Int,
      ): Body<E> = this

      fun stackTrace(
        stackTraceSerialNumber: Int,
        threadSerialNumber: Int,
      ): Elements<Body<E>> =
        object : Elements<Body<E>> {
          override fun end() = this@Body
        }

      fun heap(): Heap<Body<E>> =
        object : Heap<Body<E>> {
          override fun end() = this@Body
        }

      fun unknown(tag: Byte) = this

      fun end(): E
    }

    interface Heap<E> {
      enum class ElementType {
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        CHAR,
        BOOLEAN,
      }

      fun primitiveArray(
        id: Id,
        stackTraceSerialNumber: Int,
        numberOfElements: Int,
        elementType: ElementType,
      ): Bytes<Heap<E>> = object : Bytes<Heap<E>> {
        override fun end() = this@Heap
      }

      fun objectArray(
        id: Id,
        stackTraceSerialNumber: Int,
        numberOfElements: Int,
        arrayClassObjectId: Id,
      ): Elements<Heap<E>> = object : Elements<Heap<E>> {
        override fun end() = this@Heap
      }

      fun unknown(tag: Byte) = this

      fun end(): E
    }

    interface Bytes<E> {
      fun byte(b: Byte): Bytes<E> = this
      fun end(): E
    }

    interface Elements<E> {
      fun element(id: Id): Elements<E> = this
      fun end(): E
    }
  }
}
