// IGNORE_BACKEND_K1: JVM
// Notes: The test fails probably because it takes LV from a TC or other external settings,
// the directive `IGNORE_BACKEND_K1` should be removed after KT-69280 is fixed.

fun constant(): String {
    return """
        |Hello,
        |World
    """.trimMargin()
}

private const val HAS_MARGIN = """Hello,
        |World"""
fun interpolatedUsingConstant(): String {
    return """
        |Hello,
        |$HAS_MARGIN
        |World
    """.trimMargin()
}


private const val SPACES = "    "
private const val HELLO = "Hello"
private const val WORLD = "World"
fun reliesOnNestedStringBuilderFlatteningAndConstantConcatenation(): String {
    return ("" + '\n' + SPACES + "${SPACES}|Hey" + """
        |${HELLO + HELLO},
        |${WORLD + WORLD}
""" + SPACES).trimMargin()
}

fun constantCustomPrefix(): String {
    return """
        ###Hello,
        ###World
    """.trimMargin(marginPrefix = "###")
}

private const val OCTOTHORPE = '#'
fun constantCustomPrefixInterpolatedUsingConstant(): String {
    return """
        #@#Hello,
        #@#World
    """.trimMargin(marginPrefix = "$OCTOTHORPE@$OCTOTHORPE")
}

// 3 LDC "Hello,\\nWorld"
// 1 LDC "Hello,\\nHello,\\nWorld\\nWorld"
// 1 LDC "Hey\\nHelloHello,\\nWorldWorld"
// 0 LDC "###"
// 0 INVOKESTATIC kotlin/text/StringsKt.trimMargin
