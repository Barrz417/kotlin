package base

fun <V> nullUnless(condition: Boolean, fn: () -> V): V? = if (condition) fn() else null

fun <V> V.runIf(boolean: Boolean, fn: V.() -> V): V = if (boolean) fn() else this