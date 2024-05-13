package kdump.hprof

import java.io.*
import base.*
import kdump.hprof.*
import kdump.*
import hprof.*

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
