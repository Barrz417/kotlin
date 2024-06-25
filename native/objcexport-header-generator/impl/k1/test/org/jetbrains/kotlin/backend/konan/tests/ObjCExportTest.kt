package org.jetbrains.kotlin.backend.konan.tests

import com.intellij.openapi.util.Disposer
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.backend.konan.objcexport.getErasedTypeClass
import org.jetbrains.kotlin.backend.konan.testUtils.InlineSourceTestEnvironment
import org.jetbrains.kotlin.backend.konan.testUtils.createKotlinCoreEnvironment
import org.jetbrains.kotlin.backend.konan.testUtils.createModuleDescriptor
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi2ir.findSingleFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

abstract class ObjCExportTest : InlineSourceTestEnvironment {
    override val testDisposable = Disposer.newDisposable("${this::class.simpleName}.testDisposable")

    override val kotlinCoreEnvironment: KotlinCoreEnvironment = createKotlinCoreEnvironment(testDisposable)

    @TempDir
    override lateinit var testTempDir: File

    private lateinit var module: ModuleDescriptor

    fun createModule(@Language("kotlin") source: String): ModuleDescriptor {
        return createModuleDescriptor(source).apply {
            module = this
        }
    }

    @AfterEach
    fun dispose() {
        Disposer.dispose(testDisposable)
    }

    fun getTopLevelFun(name: String): FunctionDescriptor {
        return module.getTopLevelFun(name)
    }

    fun getTopLevelProp(name: String): PropertyDescriptor {
        return module.getTopLevelProp(name)
    }

    fun getClass(name: String): ClassDescriptor {
        return module.getClass(name)
    }

    fun getTopLevelFunExtensionType(name: String): ClassDescriptor {
        return module.getTopLevelFun(name).extensionReceiverParameter?.type?.getErasedTypeClass()
            ?: error("Unable to find top level function $name")
    }
}

internal fun ClassDescriptor.getMemberFun(name: String): FunctionDescriptor {
    return unsubstitutedMemberScope.findSingleFunction(Name.identifier(name))
}

internal fun ClassDescriptor.getMemberProperty(name: String): PropertyDescriptor {
    return unsubstitutedMemberScope.getContributedVariables(Name.identifier(name), NoLookupLocation.FROM_BACKEND).singleOrNull()
        ?: error("Unable to find property $name")
}

internal fun ModuleDescriptor.getTopLevelFun(name: String): FunctionDescriptor {
    return getPackage(FqName.ROOT).memberScope.findSingleFunction(Name.identifier(name))
}

internal fun ModuleDescriptor.getTopLevelProp(name: String): PropertyDescriptor {
    return getPackage(FqName.ROOT).memberScope.getContributedVariables(Name.identifier(name), NoLookupLocation.FROM_BACKEND).singleOrNull()
        ?: error("Unable to find property $name")
}

internal fun ModuleDescriptor.getClass(name: String): ClassDescriptor {
    return findClassAcrossModuleDependencies(ClassId.fromString(name)) ?: error("Failed to find `$name` class")
}