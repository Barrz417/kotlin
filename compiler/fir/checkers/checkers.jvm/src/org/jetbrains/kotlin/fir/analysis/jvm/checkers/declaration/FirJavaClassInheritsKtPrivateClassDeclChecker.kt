/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.jvm.checkers.declaration

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.diagnostics.jvm.FirJvmErrors.JAVA_CLASS_INHERITS_KT_PRIVATE_CLASS
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.fir.java.enhancement.javaClsInheritsKtPrivateCls
import org.jetbrains.kotlin.fir.symbols.SymbolInternals

object FirJavaClassInheritsKtPrivateClassDeclChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    override fun check(declaration: FirCallableDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration !is FirPrimaryConstructor)
            return

        @OptIn(SymbolInternals::class)
        val delegatedCtor = declaration.symbol.resolvedDelegatedConstructor?.fir?.javaClsInheritsKtPrivateCls ?: return
        if (delegatedCtor) {
            reporter.reportOn(declaration.delegatedConstructor?.source!!, JAVA_CLASS_INHERITS_KT_PRIVATE_CLASS, context)
        }
    }
}