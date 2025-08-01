/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testOld.klib

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.cliArgument
import org.jetbrains.kotlin.test.util.JUnit4Assertions
import org.jetbrains.kotlin.test.util.KtTestUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractWebKlibVersionsTest {
    lateinit var tmpdir: File

    @BeforeEach
    fun setup() {
        tmpdir = KtTestUtil.tmpDirForTest(this.javaClass.getSimpleName(), hashCode().toString())
    }

    @Test
    fun testABIVersionCLIFlag() {
        val testDataDir = File("compiler/testData/klib/resolve/mismatched-abi-version")
        val klibDir = createKlibDir("lib1")

        val correctVersions = arrayOf(
            "0.0.0", "255.255.255",
            "0.10.200", "10.200.0", "200.0.10",
            "2.2.0", "2.3.0"
        )
        for (version in correctVersions) {
            compileKlib(
                sourceFile = testDataDir.resolve("lib1.kt"),
                outputFile = klibDir,
                extraArgs = arrayOf(K2JSCompilerArguments::customKlibAbiVersion.cliArgument + "=" + version)
            ).assertSuccess()

            val manifest = File("${klibDir.absolutePath}/default/manifest")
            val versionBumped = manifest.readLines()
                .find { it.startsWith("abi_version") }
                ?.split("=")
                ?.get(1)
            assertEquals(versionBumped, version)
        }

        val incorrectVersions = arrayOf(
            "0", "0.1", "0.1.", "0.1.2.", "..", "0 .1. 2",
            "00.001.0002", "-0.-0.-0", "256.256.256"
        )
        for (version in incorrectVersions) {
            val result = compileKlib(
                sourceFile = testDataDir.resolve("lib1.kt"),
                outputFile = klibDir,
                extraArgs = arrayOf(K2JSCompilerArguments::customKlibAbiVersion.cliArgument + "=" + version)
            )
            result.assertFailure()

            val compilerOutputLines = result.output.lines()
            assertTrue(compilerOutputLines.any {
                it.contains("error: invalid ABI version")
            })
        }
    }

    @Test
    fun testMetadataVersionCLIFlag() {
        val testDataDir = File("compiler/testData/klib/resolve/mismatched-abi-version")
        val klibDir = createKlibDir("lib1")

        val correctVersions = arrayOf(
            "0.0.0", "255.255.255",
            "1.4.1", "2.1.0", "2.2.0", "2.3.0"
        )
        for (version in correctVersions) {
            compileKlib(
                sourceFile = testDataDir.resolve("lib1.kt"),
                outputFile = klibDir,
                extraArgs = arrayOf(K2JSCompilerArguments::metadataVersion.cliArgument + "=" + version)
            ).assertSuccess()

            val manifest = File("${klibDir.absolutePath}/default/manifest")
            val versionBumped = manifest.readLines()
                .find { it.startsWith("metadata_version") }
                ?.split("=")
                ?.get(1)
            assertEquals(versionBumped, version)
        }

        val incorrectVersions = arrayOf(
            "0.1.", "0.1.2.", "..", "0 .1. 2",
            // These test cases should be uncommented after fixing KT-76247
            // "0", "0.1", "0.1.2.3",
            // "00.001.0002", "-0.-0.-0", "256.256.256"
        )
        for (version in incorrectVersions) {
            val result = compileKlib(
                sourceFile = testDataDir.resolve("lib1.kt"),
                outputFile = klibDir,
                extraArgs = arrayOf(K2JSCompilerArguments::metadataVersion.cliArgument + "=" + version)
            )
            result.assertFailure()

            val compilerOutputLines = result.output.lines()
            assertTrue(compilerOutputLines.any {
                it.contains("error: invalid metadata version")
            })
        }
    }

    private fun createKlibDir(name: String): File =
        tmpdir.resolve(name).apply(File::mkdirs)

    abstract fun compileKlib(
        sourceFile: File,
        dependencies: Array<File> = emptyArray(),
        outputFile: File,
        extraArgs: Array<String> = emptyArray(),
    ): CompilationResult

    abstract fun compileToBinary(entryModuleKlib: File, dependency: File?, outputFile: File): CompilationResult

    data class CompilationResult(val exitCode: ExitCode, val output: String) {
        fun assertSuccess() = JUnit4Assertions.assertTrue(exitCode == ExitCode.OK) {
            buildString {
                appendLine("Expected exit code: ${ExitCode.OK}, Actual: $exitCode")
                appendLine("Compiler output:")
                appendLine(output)
            }
        }

        fun assertFailure() = JUnit4Assertions.assertTrue(exitCode != ExitCode.OK) {
            buildString {
                appendLine("Expected exit code: any but ${ExitCode.OK}, Actual: $exitCode")
                appendLine("Compiler output:")
                appendLine(output)
            }
        }
    }
}

