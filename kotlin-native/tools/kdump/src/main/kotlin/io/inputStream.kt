package io

import java.io.*
import base.*

/** Returns stream which reads from this stream up to given length. */
fun InputStream.newWithSize(size: Int): InputStream =
  let { base ->
    object : InputStream() {
      private var remaining = size

      override fun read(): Int =
        if (remaining == 0) -1
        else base.read().also {
          if (it != -1) {
            remaining -= 1
          }
        }
    }
  }

fun <V> InputStream.readWithSize(size: Int, fn: InputStream.() -> V): V =
  newWithSize(size).let { stream ->
    stream.fn().also { stream.skipAll() }
  }

fun InputStream.readByteInt(): Int =
  read().let { if (it == -1) throw EOFException() else it }

fun InputStream.readByte(): Byte =
  readByteInt().toByte()

fun InputStream.readShortInt(endianness: Endianness): Int {
  val i1 = readByteInt()
  val i2 = readByteInt()
  return when (endianness) {
    Endianness.LITTLE_ENDIAN -> i2.shl(8).or(i1)
    Endianness.BIG_ENDIAN -> i1.shl(8).or(i2)
  }
}

fun InputStream.readShort(endianness: Endianness): Short =
  readShortInt(endianness).toShort()

fun InputStream.readInt(endianness: Endianness): Int {
  val i1 = readShortInt(endianness)
  val i2 = readShortInt(endianness)
  return when (endianness) {
    Endianness.LITTLE_ENDIAN -> i2.shl(16).or(i1)
    Endianness.BIG_ENDIAN -> i1.shl(16).or(i2)
  }
}

fun InputStream.readLong(endianness: Endianness): Long {
  val l1 = readInt(endianness).toLong().and(0xffffffffL)
  val l2 = readInt(endianness).toLong().and(0xffffffffL)
  return when (endianness) {
    Endianness.LITTLE_ENDIAN -> l2.shl(32).or(l1)
    Endianness.BIG_ENDIAN -> l1.shl(32).or(l2)
  }
}

fun InputStream.readFloat(endianness: Endianness): Float =
  Float.fromBits(readInt(endianness))

fun InputStream.readDouble(endianness: Endianness): Double =
  Double.fromBits(readLong(endianness))

fun InputStream.readChar(endianness: Endianness): Char =
  readShortInt(endianness).toChar()

fun InputStream.byte(): Byte =
  readByte()

fun InputStream.byteInt(): Int =
  read().also { if (it == -1) throw EOFException() }

fun InputStream.short(): Short {
  val b1 = byteInt()
  val b2 = byteInt()
  return ((b1 shl 8) or b2).toShort()
}

fun InputStream.shortInt(): Int =
  short().toInt().and(0xffff)

fun InputStream.int(): Int {
  val b1 = byteInt()
  val b2 = byteInt()
  val b3 = byteInt()
  val b4 = byteInt()
  return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

fun InputStream.long(): Long {
  val i1 = int().toLong().and(0xffffffffL)
  val i2 = int().toLong().and(0xffffffffL)
  return (i1 shl 32) or i2
}

fun InputStream.float(): Float = Float.fromBits(int())
fun InputStream.double(): Double = Double.fromBits(long())
fun InputStream.char(): Char = short().toInt().and(0xffff).toChar()
fun InputStream.boolean(): Boolean = byteInt() != 0

fun InputStream.byteArray(size: Int): ByteArray =
  ByteArray(size).also {
    var off = 0
    var len = size
    while (true) {
      if (len == 0) break
      val read = read(it, off, len)
      if (read == -1) throw EOFException()
      off += read
      len -= read
    }
  }

fun InputStream.skipAll() {
  val byteArray = ByteArray(1024)
  while (true) {
    val read = read(byteArray)
    if (read == -1) break
  }
}

fun <V> InputStream.list(size: Int, fn: InputStream.() -> V): List<V> =
  buildList {
    repeat(size) {
      add(fn())
    }
  }

fun InputStream.byteArray(): ByteArray =
  ByteArrayOutputStream().also {
    val byteArray = ByteArray(1024)
    while (true) {
      val read = read(byteArray)
      if (read == -1) break
      it.write(byteArray, 0, read)
    }
  }.toByteArray()

fun InputStream.string(): String =
  byteArray().toString(Charsets.UTF_8)

fun InputStream.cString(): String =
  ByteArrayOutputStream().also {
    while (true) {
      val byte = byteInt()
      if (byte == 0) break
      it.write(byte.toInt())
    }
  }.toByteArray().toString(Charsets.UTF_8)

fun PushbackInputStream.peek(): Int =
  read().also { unread(it) }

fun PushbackInputStream.eof(): Boolean =
  peek() == -1

fun <V> PushbackInputStream.list(fn: PushbackInputStream.() -> V): List<V> =
  buildList {
    while (!eof()) {
      add(fn())
    }
  }
