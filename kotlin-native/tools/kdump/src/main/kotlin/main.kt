import collections.nextOrNull
import hprof.write
import java.io.File
import java.io.PushbackInputStream
import kdump.hprof.toHProfProfile
import kdump.item
import kdump.readDump
import text.prettyPrintln

fun main(args: Array<String>) {
  main(args.iterator())
}

fun main(args: Iterator<String>) {
  when (args.nextOrNull()) {
    "print" ->
      args.nextOrNull()?.let { pathname ->
        mainPrint(pathname)
      }
    "hprof" ->
      args.nextOrNull()?.let { inPathname ->
        args.nextOrNull()?.let { outPathname ->
          mainHprof(inPathname, outPathname)
        }
      }
    else -> null
  } ?: mainUsage()
}

fun mainUsage() {
  println("Usage:")
  println("  kdump print <kdump file>")
  println("  kdump hprof <kdump file> <hprof file>")
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
