/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.jetbrains.kotlin.gradle.plugin.diagnostics.ToolingDiagnostic.Severity.WARNING
import org.jetbrains.kotlin.gradle.plugin.diagnostics.ToolingDiagnosticFactory

object SwiftExportDiagnostics {
    object UnsupportedOs : ToolingDiagnosticFactory(WARNING) {
        operator fun invoke() = build(
            """
                Kotlin Swift Export is fully supported on MacOS machines only. Gradle tasks that can not run on non-mac hosts will be skipped.
            """.trimIndent()
        )
    }
}