package kdump

import java.io.*
import text.prettyPrintln

fun main(args: Array<String>) {
  File(args[0])
          .inputStream()
          .buffered()
          .let { PushbackInputStream(it) }
          .readDump()
          .let { prettyPrintln { item(it) } }
          //.dump()
}
