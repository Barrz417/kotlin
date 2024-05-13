package hprof

import java.io.*
import base.*

fun main() {
    File("small.hprof").readProfile().toIndex().dump()
}
