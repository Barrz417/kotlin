/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.dsl.awaitMetadataTarget
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.PSM_CONSUMABLE_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PSM_RESOLVABLE_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.categoryByName
import org.jetbrains.kotlin.gradle.plugin.launch
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.mpp.resolvableMetadataConfiguration
import org.jetbrains.kotlin.gradle.plugin.sources.InternalKotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.usageByName
import org.jetbrains.kotlin.gradle.targets.metadata.locateOrRegisterGenerateProjectStructureMetadataTask
import org.jetbrains.kotlin.gradle.utils.*
import org.jetbrains.kotlin.gradle.utils.extendsProjectDependenciesOnly
import org.jetbrains.kotlin.gradle.utils.extrasStoredProperty
import org.jetbrains.kotlin.gradle.utils.maybeCreateConsumable
import org.jetbrains.kotlin.gradle.utils.maybeCreateResolvable
import org.jetbrains.kotlin.gradle.utils.setAttribute

internal val psmAttribute = Attribute.of("psmFile", Boolean::class.javaObjectType)

/**
 * This method does two things:
 * 1) Create psm-consumable configuration for current projects, which contains psm file for this (output of psm-generation task)
 * and psm files for transitive projects as artifact.
 * 2) Adds psm file as artifact (output of psm-generation task) to `apiElements` configuration of metadata target.
 * @param project Current project
 */
internal fun setupProjectStructureMetadataOutgoingArtifacts(project: Project) {
    val psmConsumableConfiguration = maybeCreatePsmConsumableConfiguration(project)
    val generateProjectStructureMetadata = project.locateOrRegisterGenerateProjectStructureMetadataTask()

    // Adding psm generated for this project to psm-consumable configuration
    project.artifacts.add(
        psmConsumableConfiguration.name,
        generateProjectStructureMetadata.map { task -> task.resultFile }
    )

    project.launch {
        val metadataTarget = project.multiplatformExtension.awaitMetadataTarget()

        // Adding transitive dependencies from metadata target to psm-consumable configuration
        psmConsumableConfiguration.extendsProjectDependenciesOnly(
            project,
            project.configurations.getByName(metadataTarget.apiElementsConfigurationName)
        )

        // Adding psm generated for this project to the metadata `apiElements` configuration
        project.artifacts.add(
            metadataTarget.apiElementsConfigurationName,
            generateProjectStructureMetadata.map { task -> task.resultFile }
        )
    }

}

internal val InternalKotlinSourceSet.projectStructureMetadataResolvableConfiguration: Configuration by extrasStoredProperty {
    project.configurations.maybeCreateResolvable(projectStructureMetadataConfigurationName) {
        extendsProjectDependenciesOnly(project, resolvableMetadataConfiguration)
        configurePsmDependenciesAttributes(project)
    }
}

private fun maybeCreatePsmConsumableConfiguration(project: Project): Configuration {
    return project.configurations.maybeCreateConsumable(PSM_CONSUMABLE_CONFIGURATION_NAME) {
        configurePsmDependenciesAttributes(project)
    }
}

private fun Configuration.configurePsmDependenciesAttributes(project: Project) {
    attributes.setAttribute(psmAttribute, true)
    attributes.setAttribute(Usage.USAGE_ATTRIBUTE, project.usageByName(KotlinUsages.KOTLIN_PSM))
    attributes.setAttribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
}

private val InternalKotlinSourceSet.projectStructureMetadataConfigurationName: String
    get() = disambiguateName(lowerCamelCaseName(PSM_RESOLVABLE_CONFIGURATION_NAME))