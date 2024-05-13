package hprof

import java.io.*
import base.*

fun main() {
  File("heap2.hprof").readProfile().toIndexed().run {
    val indexed = this
    withDumper {
      dump(indexed, getClassDump("java/lang/Class"))
      dump(indexed, getClassDump("java/lang/Object"))
      dump(indexed, getClassDump("java/lang/String"))
      dump(indexed, getClassDump("java/lang/ref/Reference"))
      dump(indexed, getClassDump("java/util/EnumSet"))
    }
  }
}
