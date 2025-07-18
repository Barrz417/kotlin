/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle.mpp

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.kotlin
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.internals.parseKotlinSourceSetMetadataFromJson
import org.jetbrains.kotlin.gradle.plugin.mpp.GenerateProjectStructureMetadata
import org.jetbrains.kotlin.gradle.plugin.mpp.KmpIsolatedProjectsSupport
import org.jetbrains.kotlin.gradle.plugin.sources.METADATA_CONFIGURATION_NAME_SUFFIX
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.uklibs.applyMultiplatform
import org.jetbrains.kotlin.gradle.uklibs.include
import org.jetbrains.kotlin.gradle.util.replaceText
import org.jetbrains.kotlin.gradle.util.testResolveAllConfigurations
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.utils.addToStdlib.countOccurrencesOf
import org.junit.jupiter.params.ParameterizedTest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MppGradlePluginTests
class MppMetadataResolutionIT : KGPBaseTest() {

    @GradleTest
    @TestMetadata(value = "new-mpp-lib-and-app")
    fun testResolveMppLibDependencyToMetadata(gradleVersion: GradleVersion) {
        project(
            projectName = "new-mpp-lib-and-app/sample-lib",
            gradleVersion = gradleVersion,
            localRepoDir = defaultLocalRepo(gradleVersion),
            buildOptions = defaultBuildOptions.copy(
                // KT-75899 Support Gradle Project Isolation in KGP JS & Wasm
                isolatedProjects = BuildOptions.IsolatedProjectsMode.DISABLED,
            ),
        ) {
            build("publish")
        }

        project(
            projectName = "new-mpp-lib-and-app/sample-app",
            gradleVersion = gradleVersion,
            localRepoDir = defaultLocalRepo(gradleVersion),
        ) {
            buildGradle.replaceText(
                "shouldBeJs = true",
                "shouldBeJs = false",
            )
            buildGradle.append(
                """
                |kotlin.sourceSets {
                |    commonMain {
                |        dependencies {
                |            // add these dependencies to check that they are resolved to metadata
                |            api("com.example:sample-lib:1.0")
                |            compileOnly("com.example:sample-lib:1.0")
                |        }
                |    }
                |}
                """.trimMargin()
            )

            testResolveAllConfigurations { unresolvedConfigurations, buildResult ->
                assertTrue(
                    unresolvedConfigurations.isEmpty(),
                    "Expected no unresolved configurations, but found ${unresolvedConfigurations.size}: $unresolvedConfigurations",
                )

                buildResult.assertOutputContains(">> :commonMainResolvable$METADATA_CONFIGURATION_NAME_SUFFIX --> sample-lib-metadata-1.0.jar")
            }
        }
    }

    @GradleTest
    @TestMetadata(value = "new-mpp-lib-and-app")
    fun testResolveMppProjectDependencyToMetadata(gradleVersion: GradleVersion) {
        project(
            projectName = "new-mpp-lib-and-app/sample-app",
            gradleVersion = gradleVersion,
            // `resolveAllConfigurations` task is not compatible with CC and isolated projects
            buildOptions = defaultBuildOptions.disableIsolatedProjects(),
        ) {

            includeOtherProjectAsSubmodule(
                otherProjectName = "sample-lib",
                pathPrefix = "new-mpp-lib-and-app",
            )

            buildGradle.replaceText(
                """"com.example:sample-lib:1.0"""",
                """project(":sample-lib")""",
            )

            testResolveAllConfigurations { unresolvedConfigurations, buildResult ->
                assertTrue(
                    unresolvedConfigurations.isEmpty(),
                    "Expected no unresolved configurations, but found ${unresolvedConfigurations.size}: $unresolvedConfigurations",
                )

                buildResult.assertOutputContains(">> :commonMainResolvable$METADATA_CONFIGURATION_NAME_SUFFIX --> sample-lib-metadata-1.0.jar")
            }
        }
    }

    @GradleTest
    @TestMetadata(value = "kt-69310-duplicateMetadataLibrariesInClasspath")
    fun testNoDuplicateLibrariesInDiamondStructures(gradleVersion: GradleVersion) {
        project(
            projectName = "kt-69310-duplicateMetadataLibrariesInClasspath",
            gradleVersion = gradleVersion
        ) {
            build(":compileLinuxMainKotlinMetadata") {
                assertOutputDoesNotContain("""KLIB resolver.*The same 'unique_name=.*' found in more than one library""".toRegex())
                val arguments = extractNativeCompilerTaskArguments(":compileLinuxMainKotlinMetadata")
                assertEquals(
                    1,
                    arguments.countOccurrencesOf("kotlinx-kotlinx-coroutines-core-1.8.1-commonMain"),
                    "Unexpected number of kotlinx-kotlinx-coroutines-core-1.8.1-commonMain"
                )
                assertEquals(
                    1,
                    arguments.countOccurrencesOf("kotlinx-kotlinx-coroutines-core-1.8.1-concurrentMain"),
                    "Unexpected number of kotlinx-kotlinx-coroutines-core-1.8.1-concurrentMain"
                )
                assertEquals(
                    1,
                    arguments.countOccurrencesOf("kotlinx-kotlinx-coroutines-core-1.8.1-nativeMain"),
                    "Unexpected number of kotlinx-kotlinx-coroutines-core-1.8.1-concurrentMain"
                )
            }
        }
    }

    @GradleTest
    @GradleTestVersions
    @ParameterizedTest(name = "{0} isolated projects support: {1} {displayName}")
    @GradleTestExtraStringArguments("ENABLE", "DISABLE")
    fun testCustomGroupForMppPublicationInTransitiveDependencies(
        gradleVersion: GradleVersion,
        kmpIsolatedProjectsSupport: String,
    ) {
        val kmpIsolatedProjectsSupport = KmpIsolatedProjectsSupport.valueOf(kmpIsolatedProjectsSupport)
        var buildOptions = defaultBuildOptions.copy(kmpIsolatedProjectsSupport = kmpIsolatedProjectsSupport)

        if (kmpIsolatedProjectsSupport == KmpIsolatedProjectsSupport.DISABLE) {
            // See: KT-72394 (Dependency.getProjectDependency is deprecated)
            if (gradleVersion >= GradleVersion.version(TestVersions.Gradle.G_8_11)) {
                buildOptions = buildOptions.copy(warningMode = WarningMode.Summary)
            }
            buildOptions = buildOptions.copy(isolatedProjects = BuildOptions.IsolatedProjectsMode.DISABLED)
        }

        fun GradleProject.configureKotlinMultiplatform() {
            buildScriptInjection {
                project.group = "default.group"

                kotlinMultiplatform.jvm()
                kotlinMultiplatform.linuxX64()
            }
        }

        project("base-kotlin-multiplatform-library", gradleVersion) {
            includeOtherProjectAsSubmodule("base-kotlin-multiplatform-library", newSubmoduleName = "lib1") {
                configureKotlinMultiplatform()
                buildScriptInjection {
                    applyMavenPublishPlugin()
                    publishing.publications.withType(MavenPublication::class.java).configureEach {
                        if (it.name == "kotlinMultiplatform") {
                            it.groupId = "custom.group"
                            it.artifactId = "custom-artifact-id"
                        }
                    }
                }

                kotlinSourcesDir("commonMain")
                    .also { it.createDirectories() }
                    .resolve("Lib1.kt")
                    .writeText("interface Lib1")
            }

            includeOtherProjectAsSubmodule("base-kotlin-multiplatform-library", newSubmoduleName = "lib2") {
                configureKotlinMultiplatform()
                buildScriptInjection {
                    kotlinMultiplatform.sourceSets.getByName("commonMain").dependencies {
                        api(project(":lib1"))
                    }
                }

                kotlinSourcesDir("commonMain")
                    .also { it.createDirectories() }
                    .resolve("Lib2.kt")
                    .writeText("interface Lib2 : Lib1")
            }

            includeOtherProjectAsSubmodule("base-kotlin-multiplatform-library", newSubmoduleName = "lib3") {
                configureKotlinMultiplatform()
                buildScriptInjection {
                    kotlinMultiplatform.sourceSets.getByName("commonMain").dependencies {
                        api(project(":lib2"))
                    }
                }

                kotlinSourcesDir("commonMain")
                    .also { it.createDirectories() }
                    .resolve("Lib3.kt")
                    .writeText("class Lib3 : Lib2, Lib1")
            }

            build(":lib3:metadataCommonMainClasses", buildOptions = buildOptions)
        }
    }

    @GradleTest
    @GradleTestVersions(
        maxVersion = TestVersions.Gradle.G_9_0
    )
    fun `KT-77843 - ProjectDependency Project access in pre-PI KGP support`(gradleVersion: GradleVersion) {
        fun projectWithPSM(): TestProject {
            return project("empty", gradleVersion) {
                plugins {
                    kotlin("multiplatform")
                }
                buildScriptInjection {
                    project.group = "foo"
                    project.applyMultiplatform {
                        linuxX64()
                        linuxArm64()
                        sourceSets.commonMain.get().compileStubSourceWithSourceSetName()
                    }
                }
            }
        }
        val producer = projectWithPSM()
        val consumer = projectWithPSM()
        consumer.include(producer, "producer")
        consumer.buildScriptInjection {
            project.applyMultiplatform {
                sourceSets.commonMain.dependencies {
                    implementation(project(":producer"))
                }
            }
        }
        val taskName = "generateProjectStructureMetadata"
        val path = consumer.providerBuildScriptReturn {
            project.tasks.named(taskName).map {
                it as GenerateProjectStructureMetadata
                it.resultFile
            }
        }.buildAndReturn(
            taskName,
            deriveBuildOptions = {
                defaultBuildOptions.copy(
                    configurationCache = BuildOptions.ConfigurationCacheValue.DISABLED,
                    isolatedProjects = BuildOptions.IsolatedProjectsMode.DISABLED,
                    kmpIsolatedProjectsSupport = KmpIsolatedProjectsSupport.DISABLE,
                )
            },
        )
        assertEquals(
            setOf(org.jetbrains.kotlin.gradle.plugin.mpp.ModuleDependencyIdentifier("foo", "producer")),
            parseKotlinSourceSetMetadataFromJson(path.readText()).sourceSetModuleDependencies["commonMain"],
        )
    }
}
