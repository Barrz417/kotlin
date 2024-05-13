package kdump

import java.io.*
import base.*
import text.prettyPrint

fun main(args: Array<String>) {
  File(args[0])
          .inputStream()
          .buffered()
          .let { PushbackInputStream(it) }
          .readDump()
          .let { prettyPrint { item(it) } }
          //.dump()
}
