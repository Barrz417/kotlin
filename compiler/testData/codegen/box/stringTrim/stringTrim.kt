// IGNORE_BACKEND_K1: JVM
// Notes: The test fails probably because it takes LV from a TC or other external settings,
// the directive `IGNORE_BACKEND_K1` should be removed after KT-69280 is fixed.

/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
// WITH_STDLIB

import kotlin.test.*

// TODO: check IR
fun constantIndent(): String {
    return """
        Hello,
        World
    """.trimIndent()
}

fun constantMargin(): String {
    return """
        |Hello,
        |World
    """.trimMargin()
}

fun box(): String {
    assertTrue(constantIndent() === constantIndent())
    assertTrue(constantMargin() === constantMargin())

    return "OK"
}

