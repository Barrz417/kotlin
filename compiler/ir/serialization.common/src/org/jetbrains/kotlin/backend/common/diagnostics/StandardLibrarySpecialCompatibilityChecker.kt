/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.diagnostics

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.MavenComparableVersion
import org.jetbrains.kotlin.library.KotlinLibrary

/** See KT-68322 for details. */
abstract class StandardLibrarySpecialCompatibilityChecker {
    fun check(libraries: Collection<KotlinLibrary>, messageCollector: MessageCollector) {
        val compilerVersion = parseVersion(getRawCompilerVersion()) ?: return

        for (library in libraries) {
            if (isStdlib(library)) {
                val stdlibVersion = parseVersion(library.versions.compilerVersion) ?: return

                val messageToReport = getMessageToReport(compilerVersion, stdlibVersion)
                if (messageToReport != null) {
                    messageCollector.report(CompilerMessageSeverity.ERROR, messageToReport)
                }

                return
            }
        }
    }

    private fun getRawCompilerVersion(): String? {
        return customCompilerVersionForTest?.let { return it.version } ?: KotlinCompilerVersion.getVersion()
    }

    protected abstract fun isStdlib(library: KotlinLibrary): Boolean
    protected abstract fun getMessageToReport(compilerVersion: MavenComparableVersion, stdlibVersion: MavenComparableVersion): String?

    private fun parseVersion(rawVersion: String?): MavenComparableVersion? {
        return try {
            rawVersion?.let(::MavenComparableVersion)
        } catch (e: Exception) {
            null
        }
    }

    @Deprecated("Only for test purposes, use with care!")
    companion object {
        private class CustomCompilerVersionForTest(val version: String?)

        private var customCompilerVersionForTest: CustomCompilerVersionForTest? = null

        fun setUpCustomCompilerVersionForTest(compilerVersion: String?) {
            customCompilerVersionForTest = CustomCompilerVersionForTest(compilerVersion)
        }

        fun resetUpCustomCompilerVersionForTest() {
            customCompilerVersionForTest = null
        }
    }
}
