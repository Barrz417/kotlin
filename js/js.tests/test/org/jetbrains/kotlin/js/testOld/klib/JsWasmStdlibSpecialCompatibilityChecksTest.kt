/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testOld.klib

import org.jetbrains.kotlin.backend.common.diagnostics.StandardLibrarySpecialCompatibilityChecker
import org.jetbrains.kotlin.js.testOld.utils.runJsCompiler
import org.jetbrains.kotlin.library.KLIB_PROPERTY_BUILTINS_PLATFORM
import org.jetbrains.kotlin.library.KLIB_PROPERTY_COMPILER_VERSION
import org.jetbrains.kotlin.library.impl.BuiltInsPlatform
import org.jetbrains.kotlin.test.TestCaseWithTmpdir
import org.jetbrains.kotlin.test.utils.TestMessageCollector
import java.io.File
import java.util.*

class JsWasmStdlibSpecialCompatibilityChecksTest : TestCaseWithTmpdir() {
    fun testJsSameCompilerVersion() {
        listOf(
            "1.9.24",
            "2.0.0-Beta1",
            "2.0.0-Beta2",
            "2.0.0",
            "2.0.255-SNAPSHOT",
            null,
        ).forEach { version ->
            compileDummyLibrary(
                stdlibVersion = version,
                compilerVersion = version,
                isWasm = false,
                expectedWarningStatus = WarningStatus.NO_WARNINGS
            )
        }
    }

    fun testWasmSameCompilerVersion() {
        listOf(
            "1.9.24",
            "2.0.0-Beta1",
            "2.0.0-Beta2",
            "2.0.0",
            "2.0.255-SNAPSHOT",
            null,
        ).forEach { version ->
            compileDummyLibrary(
                stdlibVersion = version,
                compilerVersion = version,
                isWasm = true,
                expectedWarningStatus = WarningStatus.NO_WARNINGS
            )
        }
    }

    fun testJsNewerCompilerVersion() {
        listOf(
            "1.9.24" to "1.9.25",
            "1.9.24" to "1.10.1",
            "1.9.24" to "2.0.0-Beta1",
            "1.9.24" to "2.0.0-Beta2",
            "1.9.24" to "2.0.0",
            "1.9.24" to "2.0.255-SNAPSHOT",
            "2.0.0-Beta1" to "2.0.0-Beta2",
            "2.0.0-Beta1" to "2.0.0",
            "2.0.0-Beta1" to "2.0.255-SNAPSHOT",
            "2.0.0-Beta2" to "2.0.0",
            "2.0.0-Beta2" to "2.0.255-SNAPSHOT",
            "2.0.0" to "2.0.255-SNAPSHOT",
        ).forEach { (stdlibVersion, compilerVersion) ->
            compileDummyLibrary(
                stdlibVersion = stdlibVersion,
                compilerVersion = compilerVersion,
                isWasm = false,
                expectedWarningStatus = WarningStatus.JS_WARNING
            )
        }
    }

    fun testJsOlderCompilerVersion() {
        listOf(
            "2.0.255-SNAPSHOT" to "2.0.0",
            "2.0.255-SNAPSHOT" to "2.0.0-Beta2",
            "2.0.255-SNAPSHOT" to "2.0.0-Beta1",
            "2.0.255-SNAPSHOT" to "1.9.24",
            "2.0.0" to "2.0.0-Beta2",
            "2.0.0" to "2.0.0-Beta1",
            "2.0.0" to "1.9.24",
            "2.0.0-Beta2" to "2.0.0-Beta1",
            "2.0.0-Beta2" to "1.9.24",
            "2.0.0-Beta1" to "1.9.24",
            "1.10.1" to "1.9.24",
            "1.9.25" to "1.9.24",
        ).forEach { (stdlibVersion, compilerVersion) ->
            compileDummyLibrary(
                stdlibVersion = stdlibVersion,
                compilerVersion = compilerVersion,
                isWasm = false,
                expectedWarningStatus = WarningStatus.NO_WARNINGS
            )
        }
    }

    fun testWasmMismatchingVersions() {
        listOf(
            "2.0.255-SNAPSHOT" to "2.0.0",
            "2.0.255-SNAPSHOT" to "2.0.0-Beta2",
            "2.0.255-SNAPSHOT" to "2.0.0-Beta1",
            "2.0.255-SNAPSHOT" to "1.9.24",
            "2.0.0" to "2.0.0-Beta2",
            "2.0.0" to "2.0.0-Beta1",
            "2.0.0" to "1.9.24",
            "2.0.0-Beta2" to "2.0.0-Beta1",
            "2.0.0-Beta2" to "1.9.24",
            "2.0.0-Beta1" to "1.9.24",
            "1.10.1" to "1.9.24",
            "1.9.25" to "1.9.24",
        ).flatMap { (version1, version2) ->
            listOf(version1 to version2, version2 to version1)
        }.forEach { (stdlibVersion, compilerVersion) ->
            compileDummyLibrary(
                stdlibVersion = stdlibVersion,
                compilerVersion = compilerVersion,
                isWasm = true,
                expectedWarningStatus = WarningStatus.WASM_WARNING
            )
        }
    }

    fun testJsEitherVersionIsMissing() {
        listOf(
            "2.0.0" to null,
            null to "2.0.0",
        ).forEach { (stdlibVersion, compilerVersion) ->
            compileDummyLibrary(
                stdlibVersion = stdlibVersion,
                compilerVersion = compilerVersion,
                isWasm = false,
                expectedWarningStatus = WarningStatus.NO_WARNINGS
            )
        }
    }

    fun testWasmEitherVersionIsMissing() {
        listOf(
            "2.0.0" to null,
            null to "2.0.0",
        ).forEach { (stdlibVersion, compilerVersion) ->
            compileDummyLibrary(
                stdlibVersion = stdlibVersion,
                compilerVersion = compilerVersion,
                isWasm = true,
                expectedWarningStatus = WarningStatus.NO_WARNINGS
            )
        }
    }

    private fun compileDummyLibrary(
        stdlibVersion: String?,
        compilerVersion: String?,
        isWasm: Boolean,
        expectedWarningStatus: WarningStatus
    ) {
        val sourcesDir = createDir("sources")
        val outputDir = createDir("build")

        val sourceFile = sourcesDir.resolve("file.kt").apply { writeText("fun foo() = 42\n") }
        val moduleName = getTestName(true)

        val messageCollector = TestMessageCollector()

        withCustomCompilerVersion(compilerVersion) {
            val fakeStdlib = createFakeStdlibWithSpecificVersion(isWasm, stdlibVersion)
            runJsCompiler(messageCollector) {
                this.freeArgs = listOf(sourceFile.absolutePath)
                this.noStdlib = true // it is passed explicitly
                this.libraries = fakeStdlib.absolutePath
                this.outputDir = outputDir.absolutePath
                this.moduleName = moduleName
                this.irProduceKlibFile = true
                this.irOnly = true
                this.irModuleName = moduleName
                this.wasm = isWasm
            }
        }

        val success = when (expectedWarningStatus) {
            WarningStatus.NO_WARNINGS -> !messageCollector.hasJsWarning() && !messageCollector.hasWasmWarning()
            WarningStatus.JS_WARNING -> messageCollector.hasJsWarning(stdlibVersion!! to compilerVersion!!)
            WarningStatus.WASM_WARNING -> messageCollector.hasWasmWarning(stdlibVersion!! to compilerVersion!!)
        }

        if (!success) fail(
            buildString {
                appendLine("Compiling with stdlib=[$stdlibVersion] and compiler=[$compilerVersion]")
                appendLine("Logger compiler messages (${messageCollector.messages.size} items):")
                messageCollector.messages.joinTo(this, "\n")
            }
        )
    }

    private fun TestMessageCollector.hasJsWarning(
        specificVersions: Pair<String, String>? = null
    ): Boolean {
        val stdlibMessagePart = "Kotlin/JS standard library has an older version" + specificVersions?.first?.let { " ($it)" }.orEmpty()
        val compilerMessagePart = "than the compiler" + specificVersions?.second?.let { " ($it)" }.orEmpty()

        return messages.any { stdlibMessagePart in it.message && compilerMessagePart in it.message }
    }

    private fun TestMessageCollector.hasWasmWarning(
        specificVersions: Pair<String, String>? = null
    ): Boolean {
        val stdlibMessagePart = "The version of the Kotlin/Wasm standard library" + specificVersions?.first?.let { " ($it)" }.orEmpty()
        val compilerMessagePart = "differs from the version of the compiler" + specificVersions?.second?.let { " ($it)" }.orEmpty()

        return messages.any { stdlibMessagePart in it.message && compilerMessagePart in it.message }
    }

    private fun createFakeStdlibWithSpecificVersion(isWasm: Boolean, version: String?): File {
        val patchedStdlibDir = createDir("dependencies/stdlib-${version ?: "unknown"}")
        val manifestFile = patchedStdlibDir.resolve("default").resolve("manifest")
        if (manifestFile.exists()) return patchedStdlibDir

        val originalStdlibDir = File(System.getProperty("kotlin.js.full.stdlib.path"))
        assertTrue(originalStdlibDir.isDirectory)
        originalStdlibDir.copyRecursively(patchedStdlibDir)

        val properties = manifestFile.inputStream().use { Properties().apply { load(it) } }
        properties.remove(KLIB_PROPERTY_COMPILER_VERSION)
        if (version != null) properties[KLIB_PROPERTY_COMPILER_VERSION] = version

        if (isWasm) {
            properties[KLIB_PROPERTY_BUILTINS_PLATFORM] = BuiltInsPlatform.WASM.name
        }

        manifestFile.outputStream().use { properties.store(it, null) }

        return patchedStdlibDir
    }

    private inline fun <T> withCustomCompilerVersion(version: String?, block: () -> T): T {
        @Suppress("DEPRECATION")
        return try {
            StandardLibrarySpecialCompatibilityChecker.setUpCustomCompilerVersionForTest(version)
            block()
        } finally {
            StandardLibrarySpecialCompatibilityChecker.resetUpCustomCompilerVersionForTest()
        }
    }

    private fun createDir(name: String): File = tmpdir.resolve(name).apply { mkdirs() }

    private enum class WarningStatus { NO_WARNINGS, JS_WARNING, WASM_WARNING }
}
