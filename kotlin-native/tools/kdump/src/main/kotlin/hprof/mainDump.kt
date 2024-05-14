package hprof

import java.io.File
import text.prettyPrint

fun main(args: Array<String>) {
  File(args[0]).readProfile().let { prettyPrint { item(it) } }
}
