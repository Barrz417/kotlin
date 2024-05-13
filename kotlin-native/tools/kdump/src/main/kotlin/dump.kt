import java.io.*
import io.*

fun OutputStream.dump(bytes: ByteArray) {
  write(bytes)
}

fun OutputStream.dump(string: String) {
  dump(string.toByteArray(Charsets.UTF_8))
  dump(0.toByte())
}

fun OutputStream.dump(b: Byte) {
  write(b.toInt())
}

fun OutputStream.dump(i: Int) {
  write(i.toInt() shr 24 and 0xff)
  write(i.toInt() shr 16 and 0xff)
  write(i.toInt() shr 8 and 0xff)
  write(i.toInt() shr 0 and 0xff)
}

fun OutputStream.dump(i: Long) {
  write((i shr 56 and 0xff).toInt())
  write((i shr 48 and 0xff).toInt())
  write((i shr 40 and 0xff).toInt())
  write((i shr 32 and 0xff).toInt())
  write((i shr 24 and 0xff).toInt())
  write((i shr 16 and 0xff).toInt())
  write((i shr 8 and 0xff).toInt())
  write((i shr 0 and 0xff).toInt())
}

fun OutputStream.dump(f: Float) {
  dump(f.toBits())
}

fun OutputStream.dump(d: Double) {
  dump(d.toBits())
}

enum class Tag {
  STRING,
  HEAP_SUMMARY,
  HEAP_DUMP,
  HEAP_DUMP_SEGMENT,
  HEAP_DUMP_END,
  STACK_TRACE,
  STACK_FRAME,
}

enum class HeapDumpTag {
  CLASS_DUMP,
  INSTANCE_DUMP,
  OBJECT_ARRAY_DUMP,
  PRIMITIVE_ARRAY_DUMP,
}

val Tag.byte: Byte get() = when (this) {
  Tag.STRING -> 0x01
  Tag.HEAP_SUMMARY -> 0x07
  Tag.HEAP_DUMP -> 0x0c
  Tag.HEAP_DUMP_SEGMENT -> 0x1c
  Tag.HEAP_DUMP_END -> 0x2c
  Tag.STACK_TRACE -> 0x05
  Tag.STACK_FRAME -> 0x04
}.toByte()

val HeapDumpTag.byte: Byte get() = when (this) {
  HeapDumpTag.CLASS_DUMP -> 0x20
  HeapDumpTag.INSTANCE_DUMP -> 0x21
  HeapDumpTag.OBJECT_ARRAY_DUMP -> 0x22
  HeapDumpTag.PRIMITIVE_ARRAY_DUMP -> 0x23
}.toByte()

fun OutputStream.dump(tag: Tag) {
  dump(tag.byte)
}

fun OutputStream.dump(tag: HeapDumpTag) {
  dump(tag.byte)
}

fun OutputStream.dumpProfile(time: Long = 0, dumpBody: OutputStream.() -> Unit = {}) {
  dump("JAVA PROFILE 1.0.2")
  dump(8) // ID size
  dump(time)
  dumpBody()
}

fun OutputStream.dumpId(id: Long) {
  dump(id)
}

fun OutputStream.dumpRecord(tag: Byte, dumpBody: OutputStream.() -> Unit) {
  dump(tag)
  dump(0)
  val bytes = ByteArrayOutputStream().apply { dumpBody() }.toByteArray()
  dump(bytes.size)
  dump(bytes)
}

fun OutputStream.dumpRecord(tag: Tag, dumpBody: OutputStream.() -> Unit) {
  dumpRecord(tag.byte, dumpBody)
}

fun OutputStream.dumpRecord(tag: HeapDumpTag, dumpBody: OutputStream.() -> Unit) {
  dumpRecord(tag.byte, dumpBody)
}

fun OutputStream.dumpString(id: Long, string: String) {
  dumpRecord(Tag.STRING) {
    dumpId(id)
    write(string.toByteArray(Charsets.UTF_8))
  }
}

fun OutputStream.dumpHeapSummary(
  liveBytes: Int,
  liveInstances: Int,
  totalBytesAllocated: Long,
  totalInstancesAllocated: Long,
) {
  dumpRecord(Tag.HEAP_SUMMARY) {
    dump(liveBytes)
    dump(liveInstances)
    dump(totalBytesAllocated)
    dump(totalInstancesAllocated)
  }
}

fun OutputStream.dumpHeapDump(body: OutputStream.() -> Unit = {}) {
  dumpRecord(Tag.HEAP_DUMP, body)
}

fun OutputStream.dumpHeapDumpSegment(body: OutputStream.() -> Unit = {}) {
  dumpRecord(Tag.HEAP_DUMP_SEGMENT, body)
}

fun OutputStream.dumpHeapDumpEnd() {
  dumpRecord(Tag.HEAP_DUMP_END) {}
}

fun OutputStream.dumpStackFrame(
  id: Long,
  methodNameStringId: Long = 0,
  methodSignatureStringId: Long = 0,
  sourceFileNameStringId: Long = 0,
  classSerialNumber: Int = 1,
  lineNumber: Int = 0,
) {
  dumpRecord(Tag.STACK_FRAME) {
    dump(id)
    dump(methodNameStringId)
    dump(methodSignatureStringId)
    dump(sourceFileNameStringId)
    dump(classSerialNumber)
    dump(lineNumber)
  }
}

fun OutputStream.dumpStackTrace(
  serialNumber: Int = 1,
  threadSerialNumber: Int = 1,
  dumpFrameIds: Dumper<Long>.() -> Unit,
) {
  dumpRecord(Tag.STACK_TRACE) {
    dump(serialNumber)
    dump(threadSerialNumber)
    MutableListDumper<Long>().apply { dumpFrameIds() }.mutableList.let { list ->
      dump(list.size)
      list.forEach { dump(it) }
    }
  }
}

interface Dumper<T> {
  fun dump(t: T)
}

class MutableListDumper<T>(val mutableList: MutableList<T> = mutableListOf()): Dumper<T> {
  override fun dump(t: T) {
    mutableList.add(t)
  }
}

object Id {
  object String {
    const val MAIN = 1L
    const val MAIN_SIG = 2L
    const val FILE_NAME = 3L
  }
}

fun main() {
  File("demo.hprof").outputStream().buffered().use {
    it.run {
      dumpProfile() {
        dumpString(Id.String.MAIN, "main")
        dumpString(Id.String.MAIN_SIG, "main(String[])")
        dumpString(Id.String.FILE_NAME, "./micapolos/Foo.java")
        dumpStackFrame(1L, Id.String.MAIN, Id.String.MAIN_SIG, Id.String.FILE_NAME)
        dumpStackTrace(1, 1) {
          dump(1L)
        }
        dumpHeapSummary(0, 0, 0, 0)
        dumpHeapDumpSegment {}
        dumpHeapDumpEnd()
      }
    }
  }
  File("small.hprof").inputStream().buffered().use {
    println(
      object : HProf.Visitor<Int> {
        override fun header(time: Long) =
          object : HProf.Visitor.Body<Int> {
            override fun string(id: HProf.Id, string: String) = also {
              print("string($id): ")
              if (string.all { it.code in 32..127 }) {
                println(string)
              } else {
                println("...garbage...")
              }
            }

            override fun loadClass(
              serialNumber: Int,
              classObjectId: HProf.Id,
              stackTraceSerialNumber: Int,
              classNameStringId: HProf.Id,
            ) = also {
              println("loadClass($serialNumber, $classObjectId, $stackTraceSerialNumber, $classNameStringId)")
            }

            override fun stackFrame(
              id: HProf.Id,
              methodNameStringId: HProf.Id,
              methodSignatureStringId: HProf.Id,
              fileNameStringId: HProf.Id,
              classSerialNumber: Int,
              lineNumber: Int,
            ) = also {
              println("stackFrame($id, $methodNameStringId, $methodSignatureStringId, $fileNameStringId, $classSerialNumber, $lineNumber)")
            }

            override fun stackTrace(
              stackTraceSerialNumber: Int,
              threadSerialNumber: Int,
            ) = let { end ->
              println("stackTrace($stackTraceSerialNumber, $threadSerialNumber)")
              object : HProf.Visitor.Elements<HProf.Visitor.Body<Int>> {
                override fun element(id: HProf.Id) = also {
                  println("$id")
                }

                override fun end() = end
              }
            }

            override fun end() = 123
          }
      }.parse(it))
  }
}
