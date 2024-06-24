/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinProjectSetupAction
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.addExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.supportedTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.StaticLibrary
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XcodeEnvironment
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.registerEmbedSwiftExportTask
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl

internal object SwiftExportDSLConstants {
    const val SWIFT_EXPORT_LIBRARY_PREFIX = "swiftExport"
    const val SWIFT_EXPORT_EXTENSION_NAME = "swiftexport"
    const val TASK_GROUP = "SwiftExport"
}

@ExperimentalSwiftExportDsl
internal val SetUpSwiftExportAction = KotlinProjectSetupAction {
    if (!kotlinPropertiesProvider.swiftExportEnabled) return@KotlinProjectSetupAction
    val kotlinExtension = project.multiplatformExtension
    val swiftExportExtension = project.objects.newInstance(SwiftExportExtension::class.java, this)

    kotlinExtension.addExtension(SwiftExportDSLConstants.SWIFT_EXPORT_EXTENSION_NAME, swiftExportExtension)

    createDefaultStaticLibs(kotlinExtension)

    registerSwiftExportPipeline(project, swiftExportExtension)
}

private fun createDefaultStaticLibs(kotlinExtension: KotlinMultiplatformExtension) {
    kotlinExtension.supportedTargets().all { target ->
        target.binaries.staticLib(SwiftExportDSLConstants.SWIFT_EXPORT_LIBRARY_PREFIX) {
            baseName = project.name
        }
    }
}

@ExperimentalSwiftExportDsl
private fun registerSwiftExportPipeline(
    project: Project,
    swiftExportExtension: SwiftExportExtension,
) {
    val environment = XcodeEnvironment(project)

    project
        .multiplatformExtension
        .supportedTargets()
        .all { target ->
            target.binaries
                .matching { it.name.startsWith(SwiftExportDSLConstants.SWIFT_EXPORT_LIBRARY_PREFIX) }
                .withType(StaticLibrary::class.java).all { library ->
                    project.registerEmbedSwiftExportTask(library, environment, swiftExportExtension)
                }
        }
}