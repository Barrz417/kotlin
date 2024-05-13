import hprof.write
import java.io.File
import java.io.PushbackInputStream
import kdump.hprof.toHProfProfile
import kdump.item
import kdump.readDump
import text.prettyPrintln

fun main(args: Array<String>) {
  when (args.getOrNull(0)) {
    "print" ->
      args.getOrNull(1)?.let { pathname ->
        mainPrint(pathname)
      }
    "hprof" ->
      args.getOrNull(1)?.let { inPathname ->
        args.getOrNull(2)?.let { outPathname ->
          mainHprof(inPathname, outPathname)
        }
      }
    else -> null
  } ?: mainUsage()
}

fun mainUsage() {
  println("usage: kdump print <in>")
  println("       kdump hprof <in> <out>")
}

fun mainPrint(pathname: String) {
  File(pathname)
          .inputStream()
          .buffered()
          .let { PushbackInputStream(it) }
          .readDump()
          .let { prettyPrintln { item(it) } }
}

fun mainHprof(inPathname: String, outPathname: String) {
  File(inPathname)
          .inputStream()
          .buffered()
          .let { PushbackInputStream(it) }
          .readDump()
          .toHProfProfile()
          .run { File(outPathname).write(this) }
}
