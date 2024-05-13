package kdump

import java.io.*
import base.*

fun main(args: Array<String>) {
  File(args[0])
          .inputStream()
          .buffered()
          .let { PushbackInputStream(it) }
          .readDump()
          //.let { PrintAppendable.appendStruct { append(it) } }
          .dump()
}
