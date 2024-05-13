package io

import java.io.*
import base.*
import base.Endianness.*

fun OutputStream.write(string: String) {
  write(string.toByteArray(Charsets.UTF_8))
  write(0)
}

fun OutputStream.writeByte(b: Byte) {
  write(b.toInt())
}

fun OutputStream.writeShort(s: Short) {
  write(s.toInt().shr(8).and(0xff))
  write(s.toInt().shr(0).and(0xff))
}

fun OutputStream.writeInt(i: Int) {
  write(i.toInt() shr 24 and 0xff)
  write(i.toInt() shr 16 and 0xff)
  write(i.toInt() shr 8 and 0xff)
  write(i.toInt() shr 0 and 0xff)
}

fun OutputStream.writeLong(i: Long) {
  write((i shr 56 and 0xff).toInt())
  write((i shr 48 and 0xff).toInt())
  write((i shr 40 and 0xff).toInt())
  write((i shr 32 and 0xff).toInt())
  write((i shr 24 and 0xff).toInt())
  write((i shr 16 and 0xff).toInt())
  write((i shr 8 and 0xff).toInt())
  write((i shr 0 and 0xff).toInt())
}

fun OutputStream.writeFloat(f: Float) {
  writeInt(f.toBits())
}

fun OutputStream.writeDouble(d: Double) {
  writeLong(d.toBits())
}
