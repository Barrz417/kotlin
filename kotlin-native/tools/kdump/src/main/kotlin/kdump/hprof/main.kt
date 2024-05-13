package kdump.hprof

import hprof.write
import java.io.File
import java.io.PushbackInputStream
import kdump.readDump

fun main(args: Array<String>) {
  File(args[0])
    .inputStream()
    .buffered()
    .let { PushbackInputStream(it) }
    .readDump()
    .toHProfProfile()
    //.apply { dump() }
    .run { File(args[1]).write(this) }
}
