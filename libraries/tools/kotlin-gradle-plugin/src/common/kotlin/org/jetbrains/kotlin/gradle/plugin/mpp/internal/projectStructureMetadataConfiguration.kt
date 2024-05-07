/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.awaitMetadataTarget
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.launch
import org.jetbrains.kotlin.gradle.targets.metadata.locateOrRegisterGenerateProjectStructureMetadataTask


internal fun setupProjectStructureMetadataConsumableConfiguration(project: Project) {
    val generateProjectStructureMetadata = project.locateOrRegisterGenerateProjectStructureMetadataTask()
    project.launch {
        val metadataTarget = project.multiplatformExtension.awaitMetadataTarget()
        project.artifacts.add(
            metadataTarget.apiElementsConfigurationName,
            generateProjectStructureMetadata.map { task -> task.resultFile }
        )
    }
}