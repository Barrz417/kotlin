/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/ir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.ir.symbols.*

/**
 * Auto-generated by [org.jetbrains.kotlin.ir.generator.print.symbol.DeclaredSymbolVisitorInterfacePrinter]
 */
interface DeclaredSymbolVisitor {

    fun visitDeclaredValueParameter(symbol: IrValueParameterSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredClass(symbol: IrClassSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredAnonymousInitializer(symbol: IrAnonymousInitializerSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredTypeParameter(symbol: IrTypeParameterSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredConstructor(symbol: IrConstructorSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredEnumEntry(symbol: IrEnumEntrySymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredSimpleFunction(symbol: IrSimpleFunctionSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredProperty(symbol: IrPropertySymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredField(symbol: IrFieldSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredLocalDelegatedProperty(symbol: IrLocalDelegatedPropertySymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredScript(symbol: IrScriptSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredReplSnippet(symbol: IrReplSnippetSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredTypeAlias(symbol: IrTypeAliasSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredVariable(symbol: IrVariableSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredExternalPackageFragment(symbol: IrExternalPackageFragmentSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredFile(symbol: IrFileSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredReturnableBlock(symbol: IrReturnableBlockSymbol) { visitDeclaredSymbol(symbol) }

    fun visitDeclaredSymbol(symbol: IrSymbol)
}
