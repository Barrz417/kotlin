/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.codegen.ProjectInfo
import org.jetbrains.kotlin.klib.KlibCompilerEdition
import org.jetbrains.kotlin.klib.PartialLinkageTestUtils
import org.jetbrains.kotlin.klib.PartialLinkageTestUtils.Dependencies
import org.jetbrains.kotlin.klib.PartialLinkageTestUtils.Dependency
import org.jetbrains.kotlin.klib.PartialLinkageTestUtils.MAIN_MODULE_NAME
import org.jetbrains.kotlin.klib.PartialLinkageTestUtils.ModuleBuildDirs
import org.jetbrains.kotlin.konan.test.blackbox.support.*
import org.jetbrains.kotlin.konan.test.blackbox.support.TestCase.WithTestRunnerExtras
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.*
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationArtifact.*
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationDependencyType.*
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult.Companion.assertSuccess
import org.jetbrains.kotlin.konan.test.blackbox.support.runner.TestExecutable
import org.jetbrains.kotlin.konan.test.blackbox.support.runner.TestRunChecks
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.*
import org.jetbrains.kotlin.konan.test.blackbox.support.util.*
import org.jetbrains.kotlin.test.TargetBackend
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.opentest4j.TestAbortedException
import java.io.File

abstract class AbstractKlibLinkageTest : AbstractNativeSimpleTest() {
    protected inner class NativeTestConfiguration(testPath: String) : PartialLinkageTestUtils.TestConfiguration {
        override val testDir = getAbsoluteFile(testPath)
        override val buildDir get() = this@AbstractKlibLinkageTest.buildDir
        override val stdlibFile get() = this@AbstractKlibLinkageTest.stdlibFile
        override val targetBackend get() = TargetBackend.NATIVE

        override val testModeConstructorParameters = buildMap {
            this["isNative"] = "true"

            val cacheMode = testRunSettings.get<CacheMode>()
            when {
                cacheMode.useStaticCacheForUserLibraries -> {
                    this["staticCache"] = "TestMode.Scope.EVERYWHERE"
                    this["lazyIr"] = "TestMode.Scope.NOWHERE" // by default LazyIR is disabled
                }
                cacheMode.useStaticCacheForDistributionLibraries -> {
                    this["staticCache"] = "TestMode.Scope.DISTRIBUTION"
                    this["lazyIr"] = "TestMode.Scope.NOWHERE" // by default LazyIR is disabled
                }
            }
        }

        override fun customizeModuleSources(moduleName: String, moduleSourceDir: File) {
            if (moduleName == MAIN_MODULE_NAME)
                customizeMainModuleSources(moduleSourceDir)
        }

        override fun buildKlib(
            moduleName: String,
            buildDirs: ModuleBuildDirs,
            dependencies: Dependencies,
            klibFile: File,
            compilerEdition: KlibCompilerEdition,
            compilerArguments: List<String>,
        ) = this@AbstractKlibLinkageTest.buildKlib(
            moduleName,
            buildDirs.sourceDir,
            dependencies,
            klibFile,
            compilerEdition,
            compilerArguments
        )


        override fun buildBinaryAndRun(mainModule: Dependency, otherDependencies: Dependencies) =
            this@AbstractKlibLinkageTest.buildBinaryAndRun(mainModule, otherDependencies)

        override fun onNonEmptyBuildDirectory(directory: File) = backupDirectoryContents(directory)

        override fun onIgnoredTest() = throw TestAbortedException()
    }

    internal class ProducedKlib(val moduleName: String, val klibArtifact: KLIB, val dependencies: Dependencies) {
        override fun equals(other: Any?) = (other as? ProducedKlib)?.moduleName == moduleName
        override fun hashCode() = moduleName.hashCode()
    }

    internal val producedKlibs = linkedSetOf<ProducedKlib>() // IMPORTANT: The order makes sense!

    private val executableArtifact: Executable by lazy {
        val (_, outputDir) = PartialLinkageTestUtils.createModuleDirs(buildDir, LAUNCHER_MODULE_NAME)
        val executableFile = outputDir.resolve("app." + testRunSettings.get<KotlinNativeTargets>().testTarget.family.exeSuffix)
        Executable(executableFile)
    }

    private fun customizeMainModuleSources(moduleSourceDir: File) {
        // Add a "box" function launcher to the main module.
        moduleSourceDir.resolve(LAUNCHER_FILE_NAME).writeText(generateBoxFunctionLauncher("box"))
    }

    abstract fun buildKlib(
        moduleName: String,
        moduleSourceDir: File,
        dependencies: Dependencies,
        klibFile: File,
        compilerEdition: KlibCompilerEdition,
        compilerArguments: List<String>,
    )

    internal fun buildBinaryAndRun(mainModule: Dependency, otherDependencies: Dependencies) {
        val cacheDependencies = if (useStaticCacheForUserLibraries) {
            producedKlibs.map { producedKlib ->
                buildCacheForKlib(producedKlib)
                producedKlib.klibArtifact.toStaticCacheArtifact().toDependency()
            }
        } else
            emptyList()

        val testCase = createTestCase(
            moduleName = LAUNCHER_MODULE_NAME,
            moduleSourceDir = null, // No sources.
            compilerArgs = COMPILER_ARGS
        )

        val compilation = ExecutableCompilation(
            settings = testRunSettings,
            freeCompilerArgs = testCase.freeCompilerArgs,
            sourceModules = testCase.modules,
            extras = testCase.extras,
            dependencies = buildList {
                this += createIncludedDependency(mainModule)
                this += createLibraryDependencies(otherDependencies)
                this += cacheDependencies
            },
            expectedArtifact = executableArtifact
        )

        val compilationResult = compilation.result.assertSuccess() // <-- trigger compilation
        val executable = TestExecutable.fromCompilationResult(testCase, compilationResult)

        runExecutableAndVerify(testCase, executable) // <-- run executable and verify
    }

    private fun buildCacheForKlib(producedKlib: ProducedKlib) {
        val compilation = StaticCacheCompilation(
            settings = testRunSettings,
            freeCompilerArgs = COMPILER_ARGS,
            options = if (producedKlib.moduleName == MAIN_MODULE_NAME)
                StaticCacheCompilation.Options.ForIncludedLibraryWithTests(executableArtifact, DEFAULT_EXTRAS)
            else
                StaticCacheCompilation.Options.Regular,
            dependencies = createLibraryCacheDependencies(producedKlib.dependencies) + producedKlib.klibArtifact.toDependency(),
            expectedArtifact = producedKlib.klibArtifact.toStaticCacheArtifact()
        )

        compilation.result.assertSuccess() // <-- trigger compilation
    }

    internal fun createTestCase(moduleName: String, moduleSourceDir: File?, compilerArgs: TestCompilerArgs): TestCase {
        // Note: Don't generate a module if there are no actual sources to compile.
        val module: TestModule.Exclusive? = moduleSourceDir?.let {
            TestModule.Exclusive(
                name = moduleName,
                directRegularDependencySymbols = emptySet(), /* Don't need to pass any dependency symbols here.
                                                         Dependencies are already handled by the AbstractNativePartialLinkageTest class. */
                directFriendDependencySymbols = emptySet(),
                directDependsOnDependencySymbols = emptySet(),
            ).also { module ->
                moduleSourceDir.walk()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .forEach { file -> module.files += TestFile.createCommitted(file, module) }
            }
        }

        return TestCase(
            id = TestCaseId.Named(moduleName),
            kind = TestKind.STANDALONE,
            modules = setOfNotNull(module),
            freeCompilerArgs = compilerArgs,
            nominalPackageName = PackageName.EMPTY,
            checks = TestRunChecks.Default(testRunSettings.get<Timeouts>().executionTimeout),
            extras = DEFAULT_EXTRAS
        ).apply {
            initialize(null, null)
        }
    }

    private fun createIncludedDependency(dependency: Dependency): TestCompilationDependency<KLIB> =
        KLIB(dependency.libraryFile).toIncludedDependency()

    internal fun createLibraryDependencies(dependencies: Dependencies): Iterable<TestCompilationDependency<KLIB>> =
        dependencies.regularDependencies.map { dependency -> KLIB(dependency.libraryFile).toDependency() } +
                dependencies.friendDependencies.map { KLIB(it.libraryFile).toFriendDependency() }

    private fun createLibraryCacheDependencies(dependencies: Dependencies): Iterable<TestCompilationDependency<KLIBStaticCache>> =
        dependencies.regularDependencies.mapNotNull { dependency ->
            if (dependency.libraryFile != stdlibFile) KLIB(dependency.libraryFile).toStaticCacheArtifact().toDependency() else null
        }

    private fun KLIB.toDependency() = ExistingDependency(this, Library)
    private fun KLIB.toIncludedDependency() = ExistingDependency(this, IncludedLibrary)
    private fun KLIB.toFriendDependency() = ExistingDependency(this, FriendLibrary)
    private fun KLIBStaticCache.toDependency() = ExistingDependency(this, LibraryStaticCache)

    private fun KLIB.toStaticCacheArtifact() = KLIBStaticCacheImpl(
        cacheDir = klibFile.parentFile.resolve(STATIC_CACHE_DIR_NAME).apply { mkdirs() },
        klib = this
    )

    private val buildDir: File get() = testRunSettings.get<Binaries>().testBinariesDir
    private val stdlibFile: File get() = testRunSettings.get<KotlinNativeHome>().stdlibFile
    private val useStaticCacheForUserLibraries: Boolean get() = testRunSettings.get<CacheMode>().useStaticCacheForUserLibraries

    companion object {
        internal val COMPILER_ARGS = TestCompilerArgs(
            listOf(
                "-nostdlib", // stdlib is passed explicitly.
                "-Xsuppress-version-warnings", // Don't fail on language version warnings.
                "-Werror" // Halt on any unexpected warning.
            )
        )

        private val DEFAULT_EXTRAS = WithTestRunnerExtras(TestRunnerType.DEFAULT)

        private const val BACKED_UP_DIRECTORY_PREFIX = "__backup-"

        private fun backupDirectoryContents(directory: File) {
            val filesToBackup = directory.listFiles()?.mapNotNull { file ->
                if (file.isDirectory && file.name.startsWith(BACKED_UP_DIRECTORY_PREFIX)) null else file
            }

            if (!filesToBackup.isNullOrEmpty()) {
                val backupDirectory = directory.resolve("$BACKED_UP_DIRECTORY_PREFIX${System.currentTimeMillis()}__")
                backupDirectory.mkdirs()

                filesToBackup.forEach { file -> file.renameTo(backupDirectory.resolve(file.name)) }
            }
        }
    }
}
