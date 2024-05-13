fun main(args: Array<String>) {
  println("Converting: \"${args[0]}\" -> \"${args[1]}\"")
  kdump.hprof.main(args)
  println("Done...")
}