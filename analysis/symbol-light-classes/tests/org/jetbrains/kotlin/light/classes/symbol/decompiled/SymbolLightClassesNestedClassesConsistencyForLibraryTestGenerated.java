/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.decompiled;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency")
@TestDataPath("$PROJECT_ROOT")
public class SymbolLightClassesNestedClassesConsistencyForLibraryTestGenerated extends AbstractSymbolLightClassesNestedClassesConsistencyForLibraryTest {
  @Test
  public void testAllFilesPresentInLibraryNestedClassesConsistency() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency"), Pattern.compile("^(.+)\\.(kt)$"), null, true);
  }

  @Test
  @TestMetadata("classesAndObjects.kt")
  public void testClassesAndObjects() {
    runTest("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency/classesAndObjects.kt");
  }

  @Test
  @TestMetadata("companion.kt")
  public void testCompanion() {
    runTest("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency/companion.kt");
  }

  @Test
  @TestMetadata("namedCompanion.kt")
  public void testNamedCompanion() {
    runTest("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency/namedCompanion.kt");
  }

  @Test
  @TestMetadata("namedCompanion2.kt")
  public void testNamedCompanion2() {
    runTest("analysis/symbol-light-classes/testData/libraryNestedClassesConsistency/namedCompanion2.kt");
  }
}
