package hprof

import java.io.*
import base.*

fun main() {
  File("small.hprof")
    .readProfile()
    .let { profile ->
      File("small.rewrite.hprof").write(profile)
    }
}
