/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.GenerateNativeTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
public class CInteropFModulesTestGenerated extends AbstractNativeCInteropFModulesTest {
    @Nested
    @TestMetadata("native/native.tests/testData/CInterop/simple/simpleDefs")
    @TestDataPath("$PROJECT_ROOT")
    public class SimpleDefs {
        @Test
        public void testAllFilesPresentInSimpleDefs() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/CInterop/simple/simpleDefs"), Pattern.compile("^([^_](.+))$"), null, false);
        }

        @Test
        @TestMetadata("filterA")
        public void testFilterA() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterA/");
        }

        @Test
        @TestMetadata("filterAB")
        public void testFilterAB() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterAB/");
        }

        @Test
        @TestMetadata("filterABC")
        public void testFilterABC() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterABC/");
        }

        @Test
        @TestMetadata("filterAC")
        public void testFilterAC() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterAC/");
        }

        @Test
        @TestMetadata("filterB")
        public void testFilterB() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterB/");
        }

        @Test
        @TestMetadata("filterBC")
        public void testFilterBC() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterBC/");
        }

        @Test
        @TestMetadata("filterC")
        public void testFilterC() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/filterC/");
        }

        @Test
        @TestMetadata("full")
        public void testFull() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/full/");
        }

        @Test
        @TestMetadata("modulesA")
        public void testModulesA() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/modulesA/");
        }

        @Test
        @TestMetadata("modulesAB")
        public void testModulesAB() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/modulesAB/");
        }

        @Test
        @TestMetadata("modulesB")
        public void testModulesB() throws Exception {
            runTest("native/native.tests/testData/CInterop/simple/simpleDefs/modulesB/");
        }
    }

    @Nested
    @TestMetadata("native/native.tests/testData/CInterop/framework/frameworkDefs")
    @TestDataPath("$PROJECT_ROOT")
    public class FrameworkDefs {
        @Test
        public void testAllFilesPresentInFrameworkDefs() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/CInterop/framework/frameworkDefs"), Pattern.compile("^([^_](.+))$"), null, false);
        }

        @Test
        @TestMetadata("childImportFModules")
        public void testChildImportFModules() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/childImportFModules/");
        }

        @Test
        @TestMetadata("excludePod1")
        public void testExcludePod1() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/excludePod1/");
        }

        @Test
        @TestMetadata("excludePod1Umbrella")
        public void testExcludePod1Umbrella() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/excludePod1Umbrella/");
        }

        @Test
        @TestMetadata("explicitSubmodule")
        public void testExplicitSubmodule() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/explicitSubmodule/");
        }

        @Test
        @TestMetadata("filterPod1")
        public void testFilterPod1() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/filterPod1/");
        }

        @Test
        @TestMetadata("filterPod1A")
        public void testFilterPod1A() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/filterPod1A/");
        }

        @Test
        @TestMetadata("filterPod1Umbrella")
        public void testFilterPod1Umbrella() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/filterPod1Umbrella/");
        }

        @Test
        @TestMetadata("filterPod1UmbrellaPod1A")
        public void testFilterPod1UmbrellaPod1A() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/filterPod1UmbrellaPod1A/");
        }

        @Test
        @TestMetadata("forwardEnum")
        public void testForwardEnum() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/forwardEnum/");
        }

        @Test
        @TestMetadata("full")
        public void testFull() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/full/");
        }

        @Test
        @TestMetadata("importsAngleAngle")
        public void testImportsAngleAngle() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/importsAngleAngle/");
        }

        @Test
        @TestMetadata("importsAngleQuote")
        public void testImportsAngleQuote() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/importsAngleQuote/");
        }

        @Test
        @TestMetadata("importsQuoteAngle")
        public void testImportsQuoteAngle() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/importsQuoteAngle/");
        }

        @Test
        @TestMetadata("importsQuoteQuote")
        public void testImportsQuoteQuote() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/importsQuoteQuote/");
        }

        @Test
        @TestMetadata("modulesPod1")
        public void testModulesPod1() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/modulesPod1/");
        }

        @Test
        @TestMetadata("twoChildren")
        public void testTwoChildren() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/twoChildren/");
        }

        @Test
        @TestMetadata("visitOtherModules")
        public void testVisitOtherModules() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework/frameworkDefs/visitOtherModules/");
        }
    }

    @Nested
    @TestMetadata("native/native.tests/testData/CInterop/framework.macros/macrosDefs")
    @TestDataPath("$PROJECT_ROOT")
    public class MacrosDefs {
        @Test
        public void testAllFilesPresentInMacrosDefs() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/CInterop/framework.macros/macrosDefs"), Pattern.compile("^([^_](.+))$"), null, false);
        }

        @Test
        @TestMetadata("modulesPod1")
        public void testModulesPod1() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework.macros/macrosDefs/modulesPod1/");
        }

        @Test
        @TestMetadata("myMacroType")
        public void testMyMacroType() throws Exception {
            runTest("native/native.tests/testData/CInterop/framework.macros/macrosDefs/myMacroType/");
        }
    }

    @Nested
    @TestMetadata("native/native.tests/testData/CInterop/builtins/builtinsDefs")
    @TestDataPath("$PROJECT_ROOT")
    public class BuiltinsDefs {
        @Test
        public void testAllFilesPresentInBuiltinsDefs() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/CInterop/builtins/builtinsDefs"), Pattern.compile("^([^_](.+))$"), null, false);
        }

        @Test
        @TestMetadata("filterA")
        public void testFilterA() throws Exception {
            runTest("native/native.tests/testData/CInterop/builtins/builtinsDefs/filterA/");
        }

        @Test
        @TestMetadata("filterStdargH")
        public void testFilterStdargH() throws Exception {
            runTest("native/native.tests/testData/CInterop/builtins/builtinsDefs/filterStdargH/");
        }

        @Test
        @TestMetadata("fullA")
        public void testFullA() throws Exception {
            runTest("native/native.tests/testData/CInterop/builtins/builtinsDefs/fullA/");
        }

        @Test
        @TestMetadata("fullStdargH")
        public void testFullStdargH() throws Exception {
            runTest("native/native.tests/testData/CInterop/builtins/builtinsDefs/fullStdargH/");
        }

        @Test
        @TestMetadata("modulesA")
        public void testModulesA() throws Exception {
            runTest("native/native.tests/testData/CInterop/builtins/builtinsDefs/modulesA/");
        }
    }
}
