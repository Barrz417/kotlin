/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinProjectSetupAction
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.addExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.supportedTargets
import org.jetbrains.kotlin.gradle.plugin.diagnostics.reportDiagnostic
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportApi

internal object SwiftExportDSLConstants {
    const val SWIFT_EXPORT_EXTENSION_NAME = "swiftexport"
    const val TASK_GROUP = "SwiftExport"
}

@OptIn(ExperimentalSwiftExportApi::class)
internal val SetUpSwiftExportAction = KotlinProjectSetupAction {
    if (!kotlinPropertiesProvider.swiftExportEnabled) return@KotlinProjectSetupAction
    val kotlinExtension = project.multiplatformExtension
    val swiftExportExtension = project.objects.newInstance(SwiftExportExtension::class.java, this)

    kotlinExtension.addExtension(SwiftExportDSLConstants.SWIFT_EXPORT_EXTENSION_NAME, swiftExportExtension)

    if (!HostManager.hostIsMac) {
        reportDiagnostic(SwiftExportDiagnostics.UnsupportedOs())
    }

    registerSwiftExportTasks(project, swiftExportExtension)
}

@OptIn(ExperimentalSwiftExportApi::class)
private fun registerSwiftExportTasks(
    project: Project,
    swiftExportExtension: SwiftExportExtension,
) {
    project
        .multiplatformExtension
        .supportedTargets()
        .all { target ->
            target.binaries.withType(Framework::class.java).all { framework ->
                project.registerSwiftExportTask(
                    swiftExportExtension.name,
                    SwiftExportDSLConstants.TASK_GROUP,
                    framework
                )
            }
        }
}