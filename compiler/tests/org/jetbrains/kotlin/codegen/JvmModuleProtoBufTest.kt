/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.cliArgument
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.load.kotlin.loadModuleMapping
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import org.jetbrains.kotlin.resolve.CompilerDeserializationConfiguration
import org.jetbrains.kotlin.test.CompilerTestUtil
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File

class JvmModuleProtoBufTest : KtUsefulTestCase() {
    private fun doTest(
        relativeDirectory: String,
        compileWith: LanguageVersion = LanguageVersion.LATEST_STABLE,
        loadWith: LanguageVersion = LanguageVersion.LATEST_STABLE,
        extraOptions: List<String> = emptyList(),
        messageRenderer: MessageRenderer? = null,
    ) {
        val directory = KtTestUtil.getTestDataPathBase() + relativeDirectory
        val tmpdir = KtTestUtil.tmpDir(this::class.simpleName)

        val moduleName = "main"
        CompilerTestUtil.executeCompilerAssertSuccessful(
            K2JVMCompiler(), listOf(
                directory,
                K2JVMCompilerArguments::destination.cliArgument, tmpdir.path,
                K2JVMCompilerArguments::moduleName.cliArgument, moduleName,
                CommonCompilerArguments::languageVersion.cliArgument, compileWith.versionString
            ) + extraOptions,
            messageRenderer
        )

        val mapping = ModuleMapping.loadModuleMapping(
            File(tmpdir, "META-INF/$moduleName.${ModuleMapping.MAPPING_FILE_EXT}").readBytes(), "test",
            CompilerDeserializationConfiguration(LanguageVersionSettingsImpl(loadWith, ApiVersion.createByLanguageVersion(loadWith))),
            ::error
        )
        val result = buildString {
            for (annotationClassId in mapping.moduleData.annotations) {
                appendLine("@$annotationClassId")
            }
            for ((fqName, packageParts) in mapping.packageFqName2Parts) {
                appendLine(fqName)
                for (part in packageParts.parts) {
                    append("  ")
                    append(part)
                    val facadeName = packageParts.getMultifileFacadeName(part)
                    if (facadeName != null) {
                        append(" (")
                        append(facadeName)
                        append(")")
                    }
                    appendLine()
                }
            }
        }

        KotlinTestUtils.assertEqualsToFile(File(directory, "module-proto.txt"), result)
    }

    fun testSimple() {
        doTest("/moduleProtoBuf/simple")
    }

    fun testJvmPackageName() {
        doTest("/moduleProtoBuf/jvmPackageName")
    }

    fun testJvmPackageNameManyParts() {
        doTest("/moduleProtoBuf/jvmPackageNameManyParts")
    }

    fun testJvmPackageNameLanguageVersion11() {
        doTest("/moduleProtoBuf/jvmPackageNameLanguageVersion11", loadWith = LanguageVersion.KOTLIN_1_1)
    }

    fun testJvmPackageNameMultifileClass() {
        doTest("/moduleProtoBuf/jvmPackageNameMultifileClass")
    }
}
