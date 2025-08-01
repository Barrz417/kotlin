/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.headerklib;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.GenerateKlibNativeTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("native/native.tests/testData/klib/header-klibs/compilation")
@TestDataPath("$PROJECT_ROOT")
public class NativeHeaderKlibCompilationTestGenerated extends AbstractNativeHeaderKlibCompilationTest {
  @Test
  public void testAllFilesPresentInCompilation() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/klib/header-klibs/compilation"), Pattern.compile("^([^.]+)$"), null, false);
  }

  @Test
  @TestMetadata("anonymousObject")
  public void testAnonymousObject() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/anonymousObject/");
  }

  @Test
  @TestMetadata("classes")
  public void testClasses() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/classes/");
  }

  @Test
  @TestMetadata("clinit")
  public void testClinit() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/clinit/");
  }

  @Test
  @TestMetadata("inlineAnnotationInstantiation")
  public void testInlineAnnotationInstantiation() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineAnnotationInstantiation/");
  }

  @Test
  @TestMetadata("inlineAnonymousObject")
  public void testInlineAnonymousObject() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineAnonymousObject/");
  }

  @Test
  @TestMetadata("inlineCapture")
  public void testInlineCapture() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineCapture/");
  }

  @Test
  @TestMetadata("inlineNoRegeneration")
  public void testInlineNoRegeneration() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineNoRegeneration/");
  }

  @Test
  @TestMetadata("inlineReifiedFunction")
  public void testInlineReifiedFunction() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineReifiedFunction/");
  }

  @Test
  @TestMetadata("inlineWhenMappings")
  public void testInlineWhenMappings() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/inlineWhenMappings/");
  }

  @Test
  @TestMetadata("innerObjectRegeneration")
  public void testInnerObjectRegeneration() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/innerObjectRegeneration/");
  }

  @Test
  @TestMetadata("kt-40133")
  public void testKt_40133() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/kt-40133/");
  }

  @Test
  @TestMetadata("privateOnlyConstructors")
  public void testPrivateOnlyConstructors() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/privateOnlyConstructors/");
  }

  @Test
  @TestMetadata("privateValueClassConstructor")
  public void testPrivateValueClassConstructor() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/privateValueClassConstructor/");
  }

  @Test
  @TestMetadata("topLevel")
  public void testTopLevel() {
    runTest("native/native.tests/testData/klib/header-klibs/compilation/topLevel/");
  }
}
