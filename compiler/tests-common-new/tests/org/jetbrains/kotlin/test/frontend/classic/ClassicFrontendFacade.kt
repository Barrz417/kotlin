/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.classic

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.common.CommonDependenciesContainer
import org.jetbrains.kotlin.analyzer.common.CommonPlatformAnalyzerServices
import org.jetbrains.kotlin.analyzer.common.CommonResolverForModuleFactory
import org.jetbrains.kotlin.backend.common.reportLoadingProblemsIfAny
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.cli.js.klib.TopDownAnalyzerFacadeForJSIR
import org.jetbrains.kotlin.cli.js.klib.TopDownAnalyzerFacadeForWasm
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.codegen.forTestCompile.TestCompilePaths.KOTLIN_MOCKJDK_RUNTIME_PATH
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.JVMConfigurationKeys.JVM_TARGET
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDependenciesImpl
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.java.di.createContainerForLazyResolveWithJava
import org.jetbrains.kotlin.frontend.java.di.initJvmBuiltInsForTopDownAnalysis
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.JsFactories
import org.jetbrains.kotlin.js.config.zipFileSystemAccessor
import org.jetbrains.kotlin.library.isAnyPlatformStdlib
import org.jetbrains.kotlin.library.loader.KlibLoader
import org.jetbrains.kotlin.library.loader.KlibPlatformChecker
import org.jetbrains.kotlin.library.metadata.CurrentKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.metadata.KlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.NullFlexibleTypeDeserializer
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.native.FakeTopDownAnalyzerFacadeForNative
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.WasmTarget
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.DependencyRelation
import org.jetbrains.kotlin.test.model.FrontendFacade
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.NativeEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.getDependencies
import org.jetbrains.kotlin.test.services.configuration.nativeEnvironmentConfigurator
import org.jetbrains.kotlin.test.util.KtTestUtil
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.closure
import org.jetbrains.kotlin.wasm.config.WasmConfigurationKeys
import java.io.File

class ClassicFrontendFacade(
    testServices: TestServices
) : FrontendFacade<ClassicFrontendOutputArtifact>(testServices, FrontendKinds.ClassicFrontend) {

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::ModuleDescriptorProvider))

    private val multiplatformAnalysisConfiguration by lazy {
        MultiplatformAnalysisConfiguration(testServices)
    }

    override fun analyze(module: TestModule): ClassicFrontendOutputArtifact {
        val moduleDescriptorProvider = testServices.moduleDescriptorProvider
        val compilerConfigurationProvider = testServices.compilerConfigurationProvider
        val packagePartProviderFactory = compilerConfigurationProvider.getPackagePartProviderFactory(module)
        val project = compilerConfigurationProvider.getProject(module)
        val configuration = compilerConfigurationProvider.getCompilerConfiguration(module)

        val ktFilesMap = multiplatformAnalysisConfiguration.getKtFilesForForSourceFiles(project, module)
        val ktFiles = ktFilesMap.values.toList()

        setupJavacIfNeeded(module, ktFiles, configuration)
        @Suppress("UNCHECKED_CAST")
        val analysisResult = performResolve(
            module,
            project,
            configuration,
            packagePartProviderFactory,
            ktFiles,
            compilerEnvironment = multiplatformAnalysisConfiguration.getCompilerEnvironment(module),
            dependencyDescriptors = multiplatformAnalysisConfiguration.getDependencyDescriptors(module) as List<ModuleDescriptorImpl>,
            friendsDescriptors = multiplatformAnalysisConfiguration.getFriendDescriptors(module) as List<ModuleDescriptorImpl>,
            dependsOnDescriptors = multiplatformAnalysisConfiguration.getDependsOnDescriptors(module) as List<ModuleDescriptorImpl>
        )
        moduleDescriptorProvider.replaceModuleDescriptorForModule(module, analysisResult.moduleDescriptor)
        return ClassicFrontendOutputArtifact(
            ktFilesMap,
            analysisResult,
            project,
            module.languageVersionSettings
        )
    }

    private fun setupJavacIfNeeded(
        module: TestModule,
        ktFiles: List<KtFile>,
        configuration: CompilerConfiguration
    ) {
        if (JvmEnvironmentConfigurationDirectives.USE_JAVAC !in module.directives) return
        val mockJdk = runIf(JvmEnvironmentConfigurationDirectives.FULL_JDK !in module.directives) {
            System.getProperty(KOTLIN_MOCKJDK_RUNTIME_PATH)?.let { File(it) } ?: File(
                KtTestUtil.getHomeDirectory(),
                "compiler/testData/mockJDK/jre/lib/rt.jar"
            )
        }
        testServices.compilerConfigurationProvider.registerJavacForModule(module, ktFiles, mockJdk)
        configuration.put(JVMConfigurationKeys.USE_JAVAC, true)
    }

    private fun performResolve(
        module: TestModule,
        project: Project,
        configuration: CompilerConfiguration,
        packagePartProviderFactory: (GlobalSearchScope) -> JvmPackagePartProvider,
        files: List<KtFile>,
        compilerEnvironment: TargetEnvironment,
        dependencyDescriptors: List<ModuleDescriptorImpl>,
        friendsDescriptors: List<ModuleDescriptorImpl>,
        dependsOnDescriptors: List<ModuleDescriptorImpl>
    ): AnalysisResult {
        /*
         * K1 frontend expects for common mpp modules to have common platform as a target platform. K2 in opposite performs resolution
         * with the target platform of the leaf module, and the test system centered around it. So to fix K1 test it's needed to manually
         * configure the target platform in this case
         */
        val targetPlatform = if (
            module.languageVersionSettings.supportsFeature(LanguageFeature.MultiPlatformProjects) &&
            !module.isLeafModuleInMppGraph(testServices)
        ) {
            CommonPlatforms.defaultCommonPlatform
        } else {
            module.targetPlatform(testServices)
        }
        return when {
            targetPlatform.isJvm() -> performJvmModuleResolve(
                module, project, configuration, packagePartProviderFactory,
                files, compilerEnvironment, dependencyDescriptors, friendsDescriptors, dependencyDescriptors
            )
            targetPlatform.isJs() -> performJsModuleResolve(
                module, project, configuration, compilerEnvironment, files, dependencyDescriptors, friendsDescriptors
            )
            targetPlatform.isWasm() -> performWasmModuleResolve(
                module, project, configuration, compilerEnvironment, files, dependencyDescriptors, friendsDescriptors
            )
            targetPlatform.isNative() -> performNativeModuleResolve(
                module, project, configuration, compilerEnvironment, files, dependencyDescriptors, friendsDescriptors, dependsOnDescriptors
            )
            targetPlatform.isCommon() -> performCommonModuleResolve(
                module,
                project,
                compilerEnvironment,
                dependencyDescriptors,
                friendsDescriptors,
                files,
                targetPlatform
            )
            else -> error("Should not be here")
        }
    }

    private fun performJvmModuleResolve(
        module: TestModule,
        project: Project,
        configuration: CompilerConfiguration,
        packagePartProviderFactory: (GlobalSearchScope) -> JvmPackagePartProvider,
        files: List<KtFile>,
        compilerEnviornment: TargetEnvironment,
        dependencyDescriptors: List<ModuleDescriptorImpl>,
        friendsDescriptors: List<ModuleDescriptorImpl>,
        dependsOnDescriptors: List<ModuleDescriptorImpl>
    ): AnalysisResult {
        val moduleVisibilityManager = ModuleVisibilityManager.SERVICE.getInstance(project)
        configuration.getList(JVMConfigurationKeys.FRIEND_PATHS).forEach { moduleVisibilityManager.addFriendPath(it) }

        val moduleTrace = NoScopeRecordCliBindingTrace(project)
        if (module.dependsOnDependencies.isEmpty()) {
            @Suppress("DEPRECATION")
            return TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project,
                files,
                moduleTrace,
                configuration.copy(),
                packagePartProviderFactory,
                explicitModuleDependencyList = dependencyDescriptors,
                explicitModuleFriendsList = friendsDescriptors,
                explicitCompilerEnvironment = compilerEnviornment
            )
        }

        val moduleContentScope = GlobalSearchScope.allScope(project)
        val moduleClassResolver = SingleModuleClassResolver()
        val moduleContext = createModuleContext(module, project, dependencyDescriptors, friendsDescriptors, dependsOnDescriptors) {
            JvmBuiltIns(it, JvmBuiltIns.Kind.FROM_CLASS_LOADER)
        }
        val moduleDescriptor = moduleContext.module as ModuleDescriptorImpl
        val jvmTarget = configuration[JVM_TARGET] ?: JvmTarget.DEFAULT
        val container = createContainerForLazyResolveWithJava(
            jvmPlatform = JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget), // TODO(dsavvinov): do not pass JvmTarget around
            moduleContext = moduleContext,
            bindingTrace = moduleTrace,
            declarationProviderFactory = FileBasedDeclarationProviderFactory(moduleContext.storageManager, files),
            moduleContentScope = moduleContentScope,
            moduleClassResolver = moduleClassResolver,
            targetEnvironment = compilerEnviornment,
            lookupTracker = LookupTracker.DO_NOTHING,
            expectActualTracker = ExpectActualTracker.DoNothing,
            inlineConstTracker = InlineConstTracker.DoNothing,
            enumWhenTracker = EnumWhenTracker.DoNothing,
            packagePartProvider = packagePartProviderFactory(moduleContentScope),
            languageVersionSettings = module.languageVersionSettings,
            useBuiltInsProvider = true
        )

        container.initJvmBuiltInsForTopDownAnalysis()
        moduleClassResolver.resolver = container.get()

        moduleDescriptor.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    container.get<KotlinCodeAnalyzer>().packageFragmentProvider,
                    container.get<JavaDescriptorResolver>().packageFragmentProvider
                ),
                "CompositeProvider@ClassicFrontendFacade for $moduleDescriptor"
            )
        )

        @Suppress("DEPRECATION")
        container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)

        return AnalysisResult.success(moduleTrace.bindingContext, moduleDescriptor)
    }

    private fun createModuleDescriptorsForKlibs(
        libraryPaths: List<String>,
        klibPlatformChecker: KlibPlatformChecker,
        factories: KlibMetadataFactories,
        configuration: CompilerConfiguration,
    ): List<ModuleDescriptorImpl> {
        val result = KlibLoader {
            libraryPaths(libraryPaths)
            platformChecker(klibPlatformChecker)
            configuration.zipFileSystemAccessor?.let { zipFileSystemAccessor(it) }
        }.load()

        result.reportLoadingProblemsIfAny(configuration, allAsErrors = true)

        var builtInsModule: KotlinBuiltIns? = null
        val dependencies = mutableListOf<ModuleDescriptorImpl>()

        val storageManager = LockBasedStorageManager("ModulesStructure")

        return result.librariesStdlibFirst.map { library ->
            testServices.libraryProvider.getOrCreateStdlibByPath(library.libraryFile.path) {
                val isBuiltIns = library.isAnyPlatformStdlib

                val moduleDescriptor = factories.DefaultDeserializedDescriptorFactory.createDescriptorOptionalBuiltIns(
                    library = library,
                    languageVersionSettings = configuration.languageVersionSettings,
                    storageManager = storageManager,
                    builtIns = builtInsModule,
                    packageAccessHandler = null,
                    lookupTracker = LookupTracker.DO_NOTHING
                )
                if (isBuiltIns) builtInsModule = moduleDescriptor.builtIns
                dependencies += moduleDescriptor
                moduleDescriptor.setDependencies(ArrayList(dependencies))

                Pair(moduleDescriptor, library)
            } as ModuleDescriptorImpl
        }
    }

    private fun performJsModuleResolve(
        module: TestModule,
        project: Project,
        configuration: CompilerConfiguration,
        compilerEnvironment: TargetEnvironment,
        files: List<KtFile>,
        dependencyDescriptors: List<ModuleDescriptor>,
        friendsDescriptors: List<ModuleDescriptor>,
    ): AnalysisResult {
        val runtimeModuleDescriptors = createModuleDescriptorsForKlibs(
            libraryPaths = JsEnvironmentConfigurator.getRuntimePathsForModule(module, testServices),
            klibPlatformChecker = KlibPlatformChecker.JS,
            factories = JsFactories,
            configuration = configuration,
        )
        val transitiveLibraries = getDependencies(module, testServices, DependencyRelation.RegularDependency)
        val friendLibraries = getDependencies(module, testServices, DependencyRelation.FriendDependency)
        val allDependencies = runtimeModuleDescriptors + dependencyDescriptors + friendLibraries + friendsDescriptors + transitiveLibraries

        val builtInModuleDescriptor = allDependencies.firstNotNullOfOrNull { it.builtIns }?.builtInsModule

        @Suppress("DEPRECATION")
        return TopDownAnalyzerFacadeForJSIR.analyzeFiles(
            files,
            project,
            configuration,
            allDependencies,
            friendsDescriptors + friendLibraries,
            compilerEnvironment,
            thisIsBuiltInsModule = builtInModuleDescriptor == null,
            customBuiltInsModule = builtInModuleDescriptor
        )
    }

    private fun performWasmModuleResolve(
        module: TestModule,
        project: Project,
        configuration: CompilerConfiguration,
        compilerEnvironment: TargetEnvironment,
        files: List<KtFile>,
        dependencyDescriptors: List<ModuleDescriptor>,
        friendsDescriptors: List<ModuleDescriptor>,
    ): AnalysisResult {
        val wasmTarget = configuration.get(WasmConfigurationKeys.WASM_TARGET, WasmTarget.JS)
        val suffix = when (wasmTarget) {
            WasmTarget.JS -> "-js"
            WasmTarget.WASI -> "-wasi"
        }

        val runtimeModuleDescriptors = createModuleDescriptorsForKlibs(
            libraryPaths = listOfNotNull(
                System.getProperty("kotlin.wasm$suffix.stdlib.path")!!,
                System.getProperty("kotlin.wasm$suffix.kotlin.test.path")!!
            ),
            klibPlatformChecker = KlibPlatformChecker.Wasm(wasmTarget.alias),
            factories = JsFactories,
            configuration = configuration,
        )
        val transitiveLibraries = getDependencies(module, testServices, DependencyRelation.RegularDependency)
        val friendLibraries = getDependencies(module, testServices, DependencyRelation.FriendDependency)
        val allDependencies = runtimeModuleDescriptors + dependencyDescriptors + friendLibraries + friendsDescriptors + transitiveLibraries

        val builtInModuleDescriptor = allDependencies.firstNotNullOfOrNull { it.builtIns }?.builtInsModule

        @Suppress("DEPRECATION")
        val analyzerFacade = TopDownAnalyzerFacadeForWasm.facadeFor(wasmTarget)

        @Suppress("DEPRECATION")
        return analyzerFacade.analyzeFiles(
            files,
            project,
            configuration,
            allDependencies,
            friendsDescriptors + friendLibraries,
            compilerEnvironment,
            thisIsBuiltInsModule = builtInModuleDescriptor == null,
            customBuiltInsModule = builtInModuleDescriptor
        )
    }

    private fun performNativeModuleResolve(
        module: TestModule,
        project: Project,
        configuration: CompilerConfiguration,
        compilerEnvironment: TargetEnvironment,
        files: List<KtFile>,
        dependencyDescriptors: List<ModuleDescriptorImpl>,
        friendsDescriptors: List<ModuleDescriptorImpl>,
        dependsOnDescriptors: List<ModuleDescriptorImpl>,
    ): AnalysisResult {
        val moduleTrace = NoScopeRecordCliBindingTrace(project)
        val runtimeKlibsNames = NativeEnvironmentConfigurator.getRuntimePathsForModule(module, testServices)
        val nativeFactories = KlibMetadataFactories(::KonanBuiltIns, NullFlexibleTypeDeserializer)

        val runtimeModuleDescriptors = createModuleDescriptorsForKlibs(
            libraryPaths = runtimeKlibsNames,
            klibPlatformChecker = KlibPlatformChecker.Native(testServices.nativeEnvironmentConfigurator.getNativeTarget(module).name),
            factories = nativeFactories,
            configuration = configuration,
        )

        val stdlibBuiltInsModule = runtimeModuleDescriptors.single { it.name == Name.special("<stdlib>") }.builtIns.builtInsModule

        val moduleContext = createModuleContext(
            module, project,
            dependencyDescriptors = dependencyDescriptors + runtimeModuleDescriptors,
            friendsDescriptors = friendsDescriptors,
            dependsOnDescriptors = dependsOnDescriptors,
            capabilities = mapOf(
                // provides `klibModuleOrigin` capability needed in `ModuleDescriptor.isFromCInteropLibrary()`
                KlibModuleOrigin.CAPABILITY to CurrentKlibModuleOrigin,
            ),
        ) {
            KonanBuiltIns(it).apply {
                builtInsModule = stdlibBuiltInsModule
            }
        }
        return FakeTopDownAnalyzerFacadeForNative.analyzeFilesWithGivenTrace(
            files,
            moduleTrace,
            moduleContext,
            module.languageVersionSettings,
            compilerEnvironment
        )
    }

    private fun performCommonModuleResolve(
        module: TestModule,
        project: Project,
        compilerEnvironment: TargetEnvironment,
        dependencyDescriptors: List<ModuleDescriptorImpl>,
        friendsDescriptors: List<ModuleDescriptorImpl>,
        files: List<KtFile>,
        targetPlatform: TargetPlatform,
    ): AnalysisResult {
        val moduleDescriptor = createModuleContext(
            module, project,
            dependencyDescriptors = dependencyDescriptors,
            friendsDescriptors = friendsDescriptors,
            dependsOnDescriptors = dependencyDescriptors
        ) { DefaultBuiltIns.Instance }.module
        (moduleDescriptor as ModuleDescriptorImpl).initialize(PackageFragmentProvider.Empty)
        return CommonResolverForModuleFactory.analyzeFiles(
            files,
            Name.special("<${module.name}>"),
            dependOnBuiltIns = true,
            module.languageVersionSettings,
            targetPlatform,
            compilerEnvironment,
            dependenciesContainer = CommonDependenciesContainerImpl(moduleDescriptor)
        ) {
            val factory = testServices.compilerConfigurationProvider.getPackagePartProviderFactory(module)
            factory(it.moduleContentScope)
        }
    }

    private class CommonDependenciesContainerImpl(module: ModuleDescriptor) : CommonDependenciesContainer {
        private class ModuleInfoImpl(val module: ModuleDescriptor) : ModuleInfo {
            override val name: Name get() = module.name

            override fun dependencies(): List<ModuleInfo> = module.allDependencyModules.map(::ModuleInfoImpl)

            override fun dependencyOnBuiltIns(): ModuleInfo.DependencyOnBuiltIns = ModuleInfo.DependencyOnBuiltIns.LAST

            override val platform: TargetPlatform get() = CommonPlatforms.defaultCommonPlatform
            override val analyzerServices: PlatformDependentAnalyzerServices get() = CommonPlatformAnalyzerServices
        }

        private val _moduleInfos: List<ModuleInfoImpl> =
            ModuleInfoImpl(module).let { listOf(it) + it.module.allDependencyModules.map(::ModuleInfoImpl) }
        override val moduleInfos: List<ModuleInfo> get() = _moduleInfos

        override fun moduleDescriptorForModuleInfo(moduleInfo: ModuleInfo): ModuleDescriptor =
            _moduleInfos.singleOrNull { it.name == moduleInfo.name }?.module ?: error("Can't find ModuleDescriptor for $moduleInfo")

        override fun registerDependencyForAllModules(moduleInfo: ModuleInfo, descriptorForModule: ModuleDescriptorImpl) = Unit
        override fun packageFragmentProviderForModuleInfo(moduleInfo: ModuleInfo): PackageFragmentProvider? = null

        override val friendModuleInfos: List<ModuleInfo> = _moduleInfos.filter { it.module.shouldSeeInternalsOf(module) }
        override val refinesModuleInfos: List<ModuleInfo> = _moduleInfos.filter { it.module in module.allExpectedByModules }
    }

    private fun createModuleContext(
        module: TestModule,
        project: Project,
        dependencyDescriptors: List<ModuleDescriptorImpl>,
        friendsDescriptors: List<ModuleDescriptorImpl>,
        dependsOnDescriptors: List<ModuleDescriptorImpl>,
        capabilities: Map<ModuleCapability<*>, Any?> = emptyMap(),
        builtInsFactory: (StorageManager) -> KotlinBuiltIns,
    ): ModuleContext {
        val projectContext = ProjectContext(project, "test project context")
        val storageManager = projectContext.storageManager

        val builtIns = builtInsFactory(storageManager)
        val moduleDescriptor = ModuleDescriptorImpl(
            Name.special("<${module.name}>"), storageManager, builtIns, module.targetPlatform(testServices), capabilities
        )
        val dependencies = buildSet {
            add(moduleDescriptor)
            add(moduleDescriptor.builtIns.builtInsModule)
            addAll(dependencyDescriptors)
        }.toList()
        moduleDescriptor.setDependencies(
            ModuleDependenciesImpl(
                allDependencies = dependencies,
                modulesWhoseInternalsAreVisible = friendsDescriptors.toSet(),
                directExpectedByDependencies = dependsOnDescriptors,
                allExpectedByDependencies = dependsOnDescriptors.closure { dependsOnModuleDescriptor ->
                    dependsOnModuleDescriptor.expectedByModules.map { it as ModuleDescriptorImpl }
                }.toSet()
            ),
        )
        return projectContext.withModule(moduleDescriptor)
    }
}
