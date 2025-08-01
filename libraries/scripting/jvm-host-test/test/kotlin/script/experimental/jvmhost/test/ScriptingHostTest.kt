/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.script.experimental.jvmhost.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.cliArgument
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.CompiledScriptClassLoader
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.SCRIPT_BASE_COMPILER_ARGUMENTS_PROPERTY
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.BasicScriptingHost
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.classpathFromClass
import kotlin.script.experimental.jvmhost.*
import kotlin.script.templates.standard.SimpleScriptTemplate
import kotlin.test.*

@ResourceLock(Resources.SYSTEM_OUT)
class ScriptingHostTest {

    @Test
    fun testSimpleUsage() {
        val greeting = "Hello from script!"
        val output = captureOut {
            evalScript("println(\"$greeting\")").throwOnFailure()
        }
        assertEquals(greeting, output)
        // another API
        val output2 = captureOut {
            BasicJvmScriptingHost().evalWithTemplate<SimpleScriptTemplate>("println(\"$greeting\")".toScriptSource()).throwOnFailure()
        }
        assertEquals(greeting, output2)
    }

    @Test
    fun testSourceWithName() {
        val greeting = "Hello from script!"
        val output = captureOut {
            val basicJvmScriptingHost = BasicJvmScriptingHost()
            basicJvmScriptingHost.evalWithTemplate<SimpleScript>(
                "println(\"$greeting\")".toScriptSource("name"),
                compilation = {
                    updateClasspath(classpathFromClass<SimpleScript>())
                }
            ).throwOnFailure()
        }
        assertEquals(greeting, output)
    }

    @Test
    fun testValueResult() {
        val evalScriptWithResult = evalScriptWithResult("42")
        val resVal = evalScriptWithResult as ResultValue.Value
        assertEquals(42, resVal.value)
        assertEquals("\$\$result", resVal.name)
        assertEquals("kotlin.Int", resVal.type)
        val resField = resVal.scriptInstance!!::class.java.getDeclaredField("\$\$result")
        resField.setAccessible(true)
        assertEquals(42, resField.get(resVal.scriptInstance!!))
    }

    @Test
    fun testUnitResult() {
        val resVal = evalScriptWithResult("val x = 42")
        assertTrue(resVal is ResultValue.Unit)
    }

    @Test
    fun testErrorResult() {
        val resVal = evalScriptWithResult("throw RuntimeException(\"abc\")")
        assertTrue(resVal is ResultValue.Error)
        val resValError = resVal.error
        assertTrue(resValError is RuntimeException)
        assertEquals("abc", resValError.message)
    }

    @Test
    fun testCustomResultField() {
        val resVal = evalScriptWithResult("42", compilation = {
            resultField("outcome")
        }) as ResultValue.Value
        assertEquals("outcome", resVal.name)
        val resField = resVal.scriptInstance!!::class.java.getDeclaredField("outcome")
        assertEquals(42, resField.get(resVal.scriptInstance!!))
    }

    @Test
    fun testSaveToClasses() {
        val greeting = "Hello from script classes!"
        val outDir = Files.createTempDirectory("saveToClassesOut").toFile()
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>()
        val host = BasicJvmScriptingHost(evaluator = BasicJvmScriptClassFilesGenerator(outDir))
        host.eval("println(\"$greeting\")".toScriptSource(name = "SavedScript.kts"), compilationConfiguration, null).throwOnFailure()
        val classloader = URLClassLoader(arrayOf(outDir.toURI().toURL()), ScriptingHostTest::class.java.classLoader)
        val scriptClass = classloader.loadClass("SavedScript")
        val output = captureOut {
            scriptClass.newInstance()
        }
        assertEquals(greeting, output)
    }

    @Test
    fun testSaveToJar() {
        val greeting = "Hello from script jar!"
        val outJar = Files.createTempFile("saveToJar", ".jar").toFile()
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>()
        val host = BasicJvmScriptingHost(evaluator = BasicJvmScriptJarGenerator(outJar))
        host.eval("println(\"$greeting\")".toScriptSource(name = "SavedScript.kts"), compilationConfiguration, null).throwOnFailure()
        Thread.sleep(100)
        val classloader = URLClassLoader(arrayOf(outJar.toURI().toURL()), ScriptingHostTest::class.java.classLoader)
        val scriptClass = classloader.loadClass("SavedScript")
        val output = captureOut {
            scriptClass.newInstance()
        }
        assertEquals(greeting, output)
    }

    @Test
    fun testSaveToRunnableJar() {
        val greeting = "Hello from script jar!"
        val outJar = Files.createTempFile("saveToRunnableJar", ".jar").toFile()
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>() {
            updateClasspath(classpathFromClass<SimpleScriptTemplate>())
            updateClasspath(KotlinJars.kotlinScriptStandardJarsWithReflect)
        }
        val compiler = JvmScriptCompiler(defaultJvmScriptingHostConfiguration)
        val scriptName = "SavedRunnableScript"
        val compiledScript = runBlocking {
            compiler("println(\"$greeting\")".toScriptSource(name = "$scriptName.kts"), compilationConfiguration).throwOnFailure()
                .valueOrNull()!!
        }
        val saver = BasicJvmScriptJarGenerator(outJar)
        runBlocking {
            saver(compiledScript, ScriptEvaluationConfiguration.Default).throwOnFailure()
        }

        Thread.sleep(100)

        val classpathFromJar = run {
            val manifest = JarFile(outJar).manifest
            manifest.mainAttributes.getValue("Class-Path").split(" ") // TODO: quoted paths
                .map { File(it).toURI().toURL() }
        } + outJar.toURI().toURL()

        fun checkInvokeMain(baseClassLoader: ClassLoader?) {
            val classloader = URLClassLoader(classpathFromJar.toTypedArray(), baseClassLoader)
            val scriptClass = classloader.loadClass(scriptName)
            val mainMethod = scriptClass.methods.find { it.name == "main" }
            assertNotNull(mainMethod)
            val output = captureOutAndErr {
                mainMethod.invoke(null, emptyArray<String>())
            }.toList().filterNot(String::isEmpty).joinToString("\n")
            assertEquals(greeting, output)
        }

        checkInvokeMain(null) // isolated
        checkInvokeMain(Thread.currentThread().contextClassLoader)

        val outputFromProcess = runScriptFromJar(outJar)
        assertEquals(listOf(greeting), outputFromProcess)
    }

    @Test
    fun testSimpleRequire() {
        val greeting = "Hello from required!"
        val script = "val subj = RequiredClass().value\nprintln(\"Hello from \$subj!\")"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            importScripts(File(TEST_DATA_DIR, "importTest/requiredSrc.kt").toScriptSource())
        }
        val output = captureOut {
            BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration, null).throwOnFailure()
        }
        assertEquals(greeting, output)
    }

    @Test
    fun testSimpleImport() {
        val greeting = listOf("Hello from helloWithVal script!", "Hello from imported helloWithVal script!")
        val script = "println(\"Hello from imported \$helloScriptName script!\")"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            makeSimpleConfigurationWithTestImport()
        }
        val output = captureOut {
            BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration, null).throwOnFailure().throwOnExceptionResult()
        }.lines()
        assertEquals(greeting, output)
    }

    @Test
    fun testSimpleImportWithImplicitReceiver() {
        val greeting = listOf("Hello from helloWithVal script!", "Hello from imported helloWithVal script!")
        val script = "println(\"Hello from imported \$helloScriptName script!\")"
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                makeSimpleConfigurationWithTestImport()
                implicitReceivers(String::class)
            },
            evaluation = {
                implicitReceivers("abc")
            }
        )
        val output = captureOut {
            BasicJvmScriptingHost().eval(
                script.toScriptSource(), definition.compilationConfiguration, definition.evaluationConfiguration
            ).throwOnFailure()
        }.lines()
        assertEquals(greeting, output)
    }

    @Test
    fun testSimpleImportWithImplicitReceiverRef() {
        val greeting = listOf("Hello from helloWithVal script!", "Hello from imported helloWithVal script!")
        val script = "println(\"Hello from imported \${(::helloScriptName).get()} script!\")"
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                makeSimpleConfigurationWithTestImport()
                implicitReceivers(String::class)
            },
            evaluation = {
                implicitReceivers("abc")
            }
        )
        val output = captureOut {
            BasicJvmScriptingHost().eval(
                script.toScriptSource(), definition.compilationConfiguration, definition.evaluationConfiguration
            ).throwOnFailure()
        }.lines()
        assertEquals(greeting, output)
    }

    @Test
    fun testImplicitReceiverWithExtensionProperty() {
        // emulates the appropriate gradle kotlin dsl test
        val script = """
            val String.implicitReceiver get() = this
            require(implicitReceiver is String)
            """.trimIndent()
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                implicitReceivers(String::class)
            },
            evaluation = {
                implicitReceivers("abc")
            }
        )
        BasicJvmScriptingHost().eval(
            script.toScriptSource(), definition.compilationConfiguration, definition.evaluationConfiguration
        ).throwOnFailure()
    }

    @Test
    fun testScriptWithImplicitReceiverWithGeneric() {
        val result = listOf<String>("")
        val script = "5"
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.Wagoo::class,
                )
            },
            evaluation = {
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.Wagoo<kotlin.script.experimental.jvmhost.test.forScript.Meow>()
                )
            }
        )
        definition.evalScriptAndCheckOutput(script, result)
    }

    @Test
    fun testScriptWithUnresolvedImplicitReceiver() {
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                implicitReceivers(
                    "kotlin.script.experimental.jvmhost.test.forScript.NotExistent",
                )
            },
        )
        with(BasicJvmScriptingHost()) {
            val res = runBlocking {
                compiler("this@NotExistent.toString()".toScriptSource(), definition.compilationConfiguration)
            }
            assertTrue(res is ResultWithDiagnostics.Failure)
            assertTrue(res.reports.any { it.message in UNRESOLVED_CLASS_MESSAGES })
        }
    }

    @Test
    fun testScriptWithUnusedUnresolvedImplicitReceiver() {
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                implicitReceivers(
                    KotlinType("kotlin.script.experimental.jvmhost.test.forScript.NotExistent", isNullable = true)
                )
            },
            evaluation = {
                implicitReceivers(null)
            }
        )
        with(BasicJvmScriptingHost()) {
            val res = runBlocking {
                compiler("42".toScriptSource(), definition.compilationConfiguration)
            }
            assertTrue(res is ResultWithDiagnostics.Failure)
            assertTrue(res.reports.any { it.message in UNRESOLVED_CLASS_MESSAGES })
        }
    }

    @Test
    fun testScriptWithUnresolvedProvidedPropertyType() {
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                providedProperties(
                    "notExistent" to "kotlin.script.experimental.jvmhost.test.forScript.NotExistent",
                )
            },
        )
        with(BasicJvmScriptingHost()) {
            val res = runBlocking {
                compiler("notExistent".toScriptSource(), definition.compilationConfiguration)
            }
            if (IS_COMPILING_WITH_K2) {
                // K1 behaves differently in this case, but we don't want to touch this place, at least while there are no complaints
                assertTrue(res is ResultWithDiagnostics.Failure)
                assertTrue(res.reports.any { it.message in UNRESOLVED_CLASS_MESSAGES })
            }
        }
    }

    @Test
    fun testScriptWithProvidedPropertyAccess() {
        // we do not care about K1 behavior anymore
        if (IS_COMPILING_WITH_K2) {
            val result = evalScriptWithResult(
                """
                val bar = foo
                class A {
                    fun getFoo() = foo
                }
                bar + A().getFoo()
            """.trimIndent(),
                compilation = {
                    updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                    providedProperties(
                        "foo" to "kotlin.String",
                    )
                },
                evaluation = {
                    providedProperties("foo" to "OK")
                }
            )
            assertTrue(result is ResultValue.Value)
            assertEquals("OKOK", result.value)
        }
    }

    @Test
    fun testScriptWithUnusedUnresolvedProvidedPropertyType() {
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                providedProperties(
                    "notExistent" to KotlinType("kotlin.script.experimental.jvmhost.test.forScript.NotExistent", isNullable = true)
                )
            },
            evaluation = {
                providedProperties("notExistent" to null)
            }
        )
        with(BasicJvmScriptingHost()) {
            val res = runBlocking {
                compiler("42".toScriptSource(), definition.compilationConfiguration)
            }
            if (IS_COMPILING_WITH_K2) {
                // K1 behaves differently in this case, but we don't want to touch this place, at least while there are no complaints
                assertTrue(res is ResultWithDiagnostics.Failure)
                assertTrue(res.reports.any { it.message in UNRESOLVED_CLASS_MESSAGES })
            }
        }
    }

    @Test
    fun testScriptWithImplicitReceiversWithSameShortName() {
        val result = listOf("42")
        val script = "println(v1 + v2)"
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass::class,
                    kotlin.script.experimental.jvmhost.test.forScript.p2.TestClass::class
                )
            },
            evaluation = {
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass("4"),
                    kotlin.script.experimental.jvmhost.test.forScript.p2.TestClass("2")
                )
            }
        )
        definition.evalScriptAndCheckOutput(script, result)
    }

    @Test
    fun testScriptWithImplicitReceiversWithSameNamedProperty() {
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.TestClass1>())
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass1::class,
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass2::class
                )
            },
            evaluation = {
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass1("first"),
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass2("second")
                )
            }
        )
        definition.evalScriptAndCheckOutput("println(v)", listOf("first"))

        if (IS_COMPILING_WITH_K2) {
            // this is not supported in K1
            definition.evalScriptAndCheckOutput("println(this@TestClass2.v)", listOf("second"))
        }
    }

    @Test
    fun testScriptWithImplicitReceiverAndBaseClassWithSameNamedProperty() {
        val result = listOf("base")
        val script = "println(v)"
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.TestClass1>())
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass1::class
                )
                baseClass(
                    kotlin.script.experimental.jvmhost.test.forScript.Base::class
                )
            },
            evaluation = {
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.TestClass1("receiver")
                )
            }
        )
        definition.evalScriptAndCheckOutput(script, result)
    }

    @Test
    fun testScriptImplicitReceiversTransitiveVisibility() {
        val result = listOf("42")
        val script = """
            import kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass

            fun TestClass.foo() = 6
            
            fun test(body: TestClass.() -> Int): Int {
                return foo() *
                    body()
            }

            println(test { 7 })
        """.trimIndent()
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                updateClasspath(classpathFromClass<kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass>())
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass::class,
                )
            },
            evaluation = {
                implicitReceivers(
                    kotlin.script.experimental.jvmhost.test.forScript.p1.TestClass(""),
                )
            }
        )
        definition.evalScriptAndCheckOutput(script, result)
    }

    @Test
    fun testProvidedPropertiesNullability() {
        val stringType = KotlinType(String::class)
        val definition = createJvmScriptDefinitionFromTemplate<SimpleScriptTemplate>(
            compilation = {
                providedProperties(
                    "notNullable" to stringType,
                    "nullable" to stringType.withNullability(true)
                )
            },
            evaluation = {
                providedProperties(
                    "notNullable" to "something",
                    "nullable" to null
                )
            }
        )
        val defaultEvalConfig = definition.evaluationConfiguration
        val notNullEvalConfig = defaultEvalConfig.with {
            providedProperties("nullable" to "!")
        }
        val wrongNullEvalConfig = defaultEvalConfig.with {
            providedProperties("notNullable" to null)
        }

        with(BasicJvmScriptingHost()) {
            // compile time
            val comp0 = runBlocking {
                compiler("nullable.length".toScriptSource(), definition.compilationConfiguration)
            }
            assertTrue(comp0 is ResultWithDiagnostics.Failure)
            val errors = comp0.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }
            assertTrue( errors.any { it.message.contains( "Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type ") })

            // runtime
            fun evalWith(evalConfig: ScriptEvaluationConfiguration) =
                eval("notNullable+(nullable ?: \"0\")".toScriptSource(), definition.compilationConfiguration, evalConfig).valueOrThrow().returnValue

            val ret0 = evalWith(defaultEvalConfig)
            assertEquals("something0", (ret0 as? ResultValue.Value)?.value)

            val ret1 = evalWith(notNullEvalConfig)
            assertEquals("something!", (ret1 as? ResultValue.Value)?.value)

            val ret2 = evalWith(wrongNullEvalConfig)
            assertTrue((ret2 as? ResultValue.Error)?.error is java.lang.NullPointerException)
        }
    }

    @Test
    fun testDiamondImportWithoutSharing() {
        val greeting = listOf("Hi from common", "Hi from middle", "Hi from common", "sharedVar == 3")
        val output = doDiamondImportTest()
        assertEquals(greeting, output)
    }

    @Test
    fun testDiamondImportWithSharing() {
        val greeting = listOf("Hi from common", "Hi from middle", "sharedVar == 5")
        val output = doDiamondImportTest(
            ScriptEvaluationConfiguration {
                enableScriptsInstancesSharing()
            }
        )
        assertEquals(greeting, output)
    }

    @Test
    fun testEvalWithWrapper() {
        val greeting = "Hello from script!"
        var output = ""
        BasicJvmScriptingHost().evalWithTemplate<SimpleScriptTemplate>(
            "println(\"$greeting\")".toScriptSource(),
            {},
            {
                scriptExecutionWrapper<Any?> {
                    val outStream = ByteArrayOutputStream()
                    val prevOut = System.out
                    System.setOut(PrintStream(outStream))
                    try {
                        it()
                    } finally {
                        System.out.flush()
                        System.setOut(prevOut)
                        output = outStream.toString().trim()
                    }
                }
            }
        ).throwOnFailure()
        assertEquals(greeting, output)
    }

    @Test
    fun testKotlinPackage() {
        val greeting = "Hello from script!"
        val error = "Only the Kotlin standard library is allowed to use the 'kotlin' package"
        val script = "package kotlin\nprintln(\"$greeting\")"
        val res0 = evalScript(script)
        assertTrue(res0.reports.any { it.message == error })
        assertTrue(res0 is ResultWithDiagnostics.Failure)

        val output = captureOut {
            val res1 = evalScriptWithConfiguration(script, compilation = {
                compilerOptions(K2JVMCompilerArguments::allowKotlinPackage.cliArgument)
            }) {}
            assertTrue(res1.reports.none { it.message == error })
            assertTrue(res1 is ResultWithDiagnostics.Success)
        }
        assertEquals(greeting, output)
    }

    private fun doDiamondImportTest(evaluationConfiguration: ScriptEvaluationConfiguration? = null): List<String> {
        val mainScript = "sharedVar += 1\nprintln(\"sharedVar == \$sharedVar\")".toScriptSource("main.kts")
        val middleScript = File(TEST_DATA_DIR, "importTest/diamondImportMiddle.kts").toScriptSource()
        val commonScript = File(TEST_DATA_DIR, "importTest/diamondImportCommon.kts").toScriptSource()
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            refineConfiguration {
                beforeCompiling { ctx ->
                    when (ctx.script.name) {
                        "main.kts" -> ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                            importScripts(middleScript, commonScript)
                        }
                        "diamondImportMiddle.kts" -> ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                            importScripts(commonScript)
                        }
                        else -> ctx.compilationConfiguration
                    }.asSuccess()
                }
            }
        }
        val output = captureOut {
            BasicJvmScriptingHost().eval(mainScript, compilationConfiguration, evaluationConfiguration).throwOnFailure().throwOnExceptionResult()
        }.lines()
        return output
    }

    @Test
    fun testImportError() {
        val script = "println(\"Hello from imported \$helloScriptName script!\")"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            refineConfiguration {
                beforeCompiling { ctx ->
                    ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                        importScripts(File(TEST_DATA_DIR, "missing_script.kts").toScriptSource())
                    }.asSuccess()
                }
            }
        }
        val res = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration, null)
        assertTrue(res is ResultWithDiagnostics.Failure)
        val report = res.reports.find { it.message.startsWith("Imported source file not found") }
        assertNotNull(report)
        assertEquals("script.kts", report.sourcePath)
    }

    @Test
    fun testCompileOptionsLanguageVersion() {
        val script = """
            fun test() {
                while (true) {
                    run {
                        break
                    }
                }
            }
        """.trimIndent()
        val compilationConfiguration1 = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            compilerOptions(
                CommonCompilerArguments::languageVersion.cliArgument,
                LanguageVersion.FIRST_SUPPORTED.versionString,
                CommonCompilerArguments::suppressVersionWarnings.cliArgument,
                CommonCompilerArguments::whenGuards.cliArgument,
            )
        }
        val res = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration1, null)
        assertTrue(res is ResultWithDiagnostics.Failure)
        res.reports.find { it.message.startsWith("The feature \"break continue in inline lambdas\" is only available since language version 2.2") }
            ?: fail("Error report about language version not found. Reported:\n  ${res.reports.joinToString("\n  ") { it.message }}")
    }

    @Test
    fun testCompileOptionsNoStdlib() {
        val script = "println(\"Hi\")"

        val res1 = evalScriptWithConfiguration(script, compilation = {
            compilerOptions(K2JVMCompilerArguments::noStdlib.cliArgument)
        }) {}
        assertTrue(res1 is ResultWithDiagnostics.Failure)
        val regex = "Unresolved reference\\W+println".toRegex()
        res1.reports.find { it.message.contains(regex) }
            ?: fail("Expected unresolved reference report. Reported:\n  ${res1.reports.joinToString("\n  ") { it.message }}")

        val res2 = evalScriptWithConfiguration(script, compilation = {
            refineConfiguration {
                beforeCompiling { ctx ->
                    ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                        compilerOptions(K2JVMCompilerArguments::noStdlib.cliArgument)
                    }.asSuccess()
                }
            }
        }) {}
        // -no-stdlib in refined configuration has no effect
        assertTrue(res2 is ResultWithDiagnostics.Success)
    }

    @Test
    fun testErrorOnParsingOptions() {
        val script = "println(\"Hi\")"

        val compilationConfiguration1 = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            compilerOptions("-jvm-target->1.8")
        }
        val res1 = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration1, null)
        assertTrue(res1 is ResultWithDiagnostics.Failure)
        assertNotNull(res1.reports.find { it.message == "Invalid argument: -jvm-target->1.8" })

        val compilationConfiguration2 = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            refineConfiguration {
                beforeCompiling { ctx ->
                    ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                        compilerOptions.append("-jvm-target->1.6")
                    }.asSuccess()
                }
            }
        }
        val res2 = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration2, null)
        assertTrue(res2 is ResultWithDiagnostics.Failure)
        assertNotNull(res2.reports.find { it.message == "Invalid argument: -jvm-target->1.6" })
    }

    @Test
    fun testInvalidOptionsWarning() {
        val script = "1"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            compilerOptions("-Xunknown1")
            refineConfiguration {
                beforeCompiling { ctx ->
                    ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                        compilerOptions.append("-Xunknown2")
                    }.asSuccess()
                }
            }
        }
        val res = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration, null)
        assertTrue(res is ResultWithDiagnostics.Success)
        assertNotNull(res.reports.find { it.message == "Flag is not supported by this version of the compiler: -Xunknown1" })
        assertNotNull(res.reports.find { it.message == "Flag is not supported by this version of the compiler: -Xunknown2" })
    }

    @Test
    fun testIgnoredOptionsWarning() {
        val script = "println(\"Hi\")"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            compilerOptions(
                K2JVMCompilerArguments::version.cliArgument,
                K2JVMCompilerArguments::destination.cliArgument,
                "destDir",
                K2JVMCompilerArguments::reportPerf.cliArgument,
                K2JVMCompilerArguments::noReflect.cliArgument
            )
            refineConfiguration {
                beforeCompiling { ctx ->
                    ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                        compilerOptions.append(
                            K2JVMCompilerArguments::noJdk.cliArgument,
                            K2JVMCompilerArguments::version.cliArgument,
                            K2JVMCompilerArguments::noStdlib.cliArgument,
                            K2JVMCompilerArguments::dumpPerf.cliArgument,
                            K2JVMCompilerArguments::noInline.cliArgument
                        )
                    }.asSuccess()
                }
            }
        }
        val res = BasicJvmScriptingHost().eval(script.toScriptSource(), compilationConfiguration, null)
        assertTrue(res is ResultWithDiagnostics.Success)
        assertNotNull(res.reports.find { it.message == "The following compiler arguments are ignored on script compilation: ${K2JVMCompilerArguments::version.cliArgument}, ${K2JVMCompilerArguments::destination.cliArgument}, ${K2JVMCompilerArguments::reportPerf.cliArgument}" })
        assertNotNull(res.reports.find { it.message == "The following compiler arguments are ignored on script compilation: ${K2JVMCompilerArguments::dumpPerf.cliArgument}" })
        assertNotNull(res.reports.find { it.message == "The following compiler arguments are ignored when configured from refinement callbacks: ${K2JVMCompilerArguments::noJdk.cliArgument}, ${K2JVMCompilerArguments::noStdlib.cliArgument}" })
    }

    fun jvmTargetTestImpl(target: String, expectedVersion: Int) {
        val script = "println(\"Hi\")"
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
            compilerOptions(K2JVMCompilerArguments::jvmTarget.cliArgument, target)
        }
        val compiler = JvmScriptCompiler(defaultJvmScriptingHostConfiguration)
        val compiledScript = runBlocking { compiler(script.toScriptSource(name = "SavedScript.kts"), compilationConfiguration) }
        assertTrue(compiledScript is ResultWithDiagnostics.Success)

        val jvmCompiledScript = compiledScript.valueOrNull()!! as KJvmCompiledScript
        val jvmCompiledModule = jvmCompiledScript.getCompiledModule() as KJvmCompiledModuleInMemoryImpl
        val bytes = jvmCompiledModule.compilerOutputFiles["SavedScript.class"]!!

        var classFileVersion: Int? = null
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.API_VERSION) {
            override fun visit(
                version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?
            ) {
                classFileVersion = version
            }
        }, 0)

        assertEquals(expectedVersion, classFileVersion)
    }

    @Test
    fun testJvmTarget() {
        jvmTargetTestImpl("1.8", 52)
        jvmTargetTestImpl("9", 53)
        jvmTargetTestImpl("17", 61)
    }

    @Test
    fun testCompiledScriptClassLoader() {
        val script = "val x = 1"
        val scriptCompilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>()
        val compiler = JvmScriptCompiler(defaultJvmScriptingHostConfiguration)
        val compiledScript = runBlocking {
            val res = compiler(script.toScriptSource(), scriptCompilationConfiguration).throwOnFailure()
            (res as ResultWithDiagnostics.Success<CompiledScript>).value
        }
        val compiledScriptClass = runBlocking { compiledScript.getClass(null).throwOnFailure().valueOrNull()!! }
        val classLoader = compiledScriptClass.java.classLoader

        assertTrue(classLoader is CompiledScriptClassLoader)
        val anotherClass = classLoader.loadClass(compiledScriptClass.qualifiedName)

        assertEquals(compiledScriptClass.java, anotherClass)

        val classResourceName = compiledScriptClass.qualifiedName!!.replace('.', '/') + ".class"
        val classAsResourceUrl = classLoader.getResource(classResourceName)
        val classAssResourceStream = classLoader.getResourceAsStream(classResourceName)

        assertNotNull(classAsResourceUrl)
        assertNotNull(classAssResourceStream)

        val classAsResourceData = classAsResourceUrl.openConnection().getInputStream().readBytes()
        val classAsResourceStreamData = classAssResourceStream.readBytes()

        assertContentEquals(classAsResourceData, classAsResourceStreamData)

        // TODO: consider testing getResources as well
    }
}

internal fun runScriptFromJar(jar: File): List<String> {
    val javaExecutable = File(File(System.getProperty("java.home"), "bin"), "java")
    val args = listOf(javaExecutable.absolutePath, "-jar", jar.path)
    val processBuilder = ProcessBuilder(args)
    processBuilder.redirectErrorStream(true)
    val r = run {
        val process = processBuilder.start()
        process.waitFor(10, TimeUnit.SECONDS)
        val out = process.inputStream.reader().readText()
        if (process.isAlive) {
            process.destroyForcibly()
            "Error: timeout, killing script process\n$out"
        } else {
            out
        }
    }.trim()
    return r.lineSequence().map { it.trim() }.toList()
}

fun <T> ResultWithDiagnostics<T>.throwOnFailure(): ResultWithDiagnostics<T> = apply {
    if (this is ResultWithDiagnostics.Failure) {
        val firstExceptionFromReports = reports.find { it.exception != null }?.exception
        throw Exception(
            "Compilation/evaluation failed:\n  ${reports.joinToString("\n  ") { it.exception?.toString() ?: it.message }}",
            firstExceptionFromReports
        )
    }
}

fun <T> ResultWithDiagnostics<T>.throwOnExceptionResult(): ResultWithDiagnostics<T> = apply {
    if (this is ResultWithDiagnostics.Success) {
        val result = (this.value as? EvaluationResult)
        val error = (result?.returnValue as? ResultValue.Error)?.error
        if (error != null) throw Exception(
            "Evaluation failed:\n  ${reports.joinToString("\n  ") { it.exception?.toString() ?: it.message }}",
            error
        )
    }
}

private fun evalScript(script: String, host: BasicScriptingHost = BasicJvmScriptingHost()): ResultWithDiagnostics<*> =
    evalScriptWithConfiguration(script, host) {}

private fun evalScriptWithResult(
    script: String,
    host: BasicScriptingHost = BasicJvmScriptingHost(),
    compilation: ScriptCompilationConfiguration.Builder.() -> Unit = {},
    evaluation: ScriptEvaluationConfiguration.Builder.() -> Unit = {}
): ResultValue =
    evalScriptWithConfiguration(script, host, compilation, evaluation).throwOnFailure().valueOrNull()!!.returnValue

internal fun evalScriptWithConfiguration(
    script: String,
    host: BasicScriptingHost = BasicJvmScriptingHost(),
    compilation: ScriptCompilationConfiguration.Builder.() -> Unit = {},
    evaluation: ScriptEvaluationConfiguration.Builder.() -> Unit = {}
): ResultWithDiagnostics<EvaluationResult> {
    val compilationConf = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>(body = compilation)
    val evaluationConf = createJvmEvaluationConfigurationFromTemplate<SimpleScriptTemplate>(body = evaluation)
    return host.eval(script.toScriptSource(), compilationConf, evaluationConf)
}

private fun ScriptDefinition.evalScriptAndCheckOutput(script: String, expectedOutput: List<String>) {
    val output = captureOut {
        val retVal = BasicJvmScriptingHost().eval(
            script.toScriptSource(), compilationConfiguration, evaluationConfiguration
        ).valueOrThrow().returnValue
        if (retVal is ResultValue.Error) throw retVal.error
    }.lines()
    assertEquals(expectedOutput, output)
}

internal fun ScriptCompilationConfiguration.Builder.makeSimpleConfigurationWithTestImport() {
    updateClasspath(classpathFromClass<ScriptingHostTest>()) // the lambda below should be in the classpath
    refineConfiguration {
        beforeCompiling { ctx ->
            val importedScript = File(TEST_DATA_DIR, "importTest/helloWithVal.kts")
            if ((ctx.script as? FileBasedScriptSource)?.file?.canonicalFile == importedScript.canonicalFile) {
                ctx.compilationConfiguration
            } else {
                ScriptCompilationConfiguration(ctx.compilationConfiguration) {
                    importScripts(importedScript.toScriptSource())
                }
            }.asSuccess()
        }
    }
}

internal fun captureOut(body: () -> Unit): String = captureOutAndErr(body).first

internal fun captureOutAndErr(body: () -> Unit): Pair<String, String> {
    val outStream = ByteArrayOutputStream()
    val errStream = ByteArrayOutputStream()
    val prevOut = System.out
    val prevErr = System.err
    System.setOut(PrintStream(outStream))
    System.setErr(PrintStream(errStream))
    try {
        body()
    } finally {
        System.out.flush()
        System.err.flush()
        System.setOut(prevOut)
        System.setErr(prevErr)
    }
    return outStream.toString().trim() to errStream.toString().trim()
}

private val UNRESOLVED_CLASS_MESSAGES = arrayOf(
    "unable to load class kotlin.script.experimental.jvmhost.test.forScript.NotExistent", // K1
    "Unresolved reference 'kotlin.script.experimental.jvmhost.test.forScript.NotExistent'.", // K2
)

private val IS_COMPILING_WITH_K2 =
    System.getProperty(SCRIPT_BASE_COMPILER_ARGUMENTS_PROPERTY)?.contains("-language-version 1.9") != true

