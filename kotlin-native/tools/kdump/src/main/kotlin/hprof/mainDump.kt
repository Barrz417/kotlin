package hprof

import java.io.*
import base.*

fun main(args: Array<String>) {
  File(args[0]).readProfile().dump()
}
