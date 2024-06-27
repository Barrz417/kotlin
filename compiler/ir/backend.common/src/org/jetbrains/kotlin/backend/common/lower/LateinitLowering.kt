/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.Symbols
import org.jetbrains.kotlin.backend.common.phaser.PhaseDescription
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

@PhaseDescription(
    name = "LateinitLowering",
    description = "Lower lateinit properties and variables"
)
class LateinitLowering(private val context: CommonBackendContext) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(Transformer(context))
    }

    fun lower(irBody: IrBody) {
        irBody.transformChildrenVoid(Transformer(context))
    }

    private class Transformer(private val backendContext: CommonBackendContext) : IrElementTransformerVoid() {
        override fun visitProperty(declaration: IrProperty): IrStatement {
            if (declaration.isRealLateinit()) {
                val backingField = declaration.backingField!!
                transformBackingField(backingField, declaration)

                declaration.getter?.let {
                    transformGetter(backingField, it)
                }
            }

            declaration.transformChildrenVoid()
            return declaration
        }

        override fun visitVariable(declaration: IrVariable): IrStatement {
            declaration.transformChildrenVoid()

            if (declaration.isLateinit) {
                declaration.type = declaration.type.makeNullable()
                declaration.isVar = true
                declaration.initializer =
                    IrConstImpl.constNull(declaration.startOffset, declaration.endOffset, backendContext.irBuiltIns.nothingNType)
            }

            return declaration
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            expression.transformChildrenVoid()

            val irValue = expression.symbol.owner
            if (irValue !is IrVariable || !irValue.isLateinit) {
                return expression
            }

            return backendContext.createIrBuilder(
                (irValue.parent as IrSymbolOwner).symbol,
                expression.startOffset,
                expression.endOffset
            ).run {
                irIfThenElse(
                    expression.type,
                    irEqualsNull(irGet(irValue)),
                    backendContext.throwUninitializedPropertyAccessException(this, irValue.name.asString()),
                    irGet(irValue)
                )
            }
        }

        override fun visitGetField(expression: IrGetField): IrExpression {
            expression.transformChildrenVoid()

            val irField = expression.symbol.owner
            if (irField.isLateinitBackingField()) {
                expression.type = expression.type.makeNullable()
            }
            return expression
        }

        private fun IrField.isLateinitBackingField(): Boolean {
            val property = this.correspondingPropertySymbol?.owner
            return property != null && property.isRealLateinit()
        }

        private fun IrProperty.isRealLateinit() =
            isLateinit && !isFakeOverride

        override fun visitCall(expression: IrCall): IrExpression {
            expression.transformChildrenVoid()

            if (!Symbols.isLateinitIsInitializedPropertyGetter(expression.symbol)) return expression

            return expression.extensionReceiver!!.replaceTailExpression {
                val irPropertyRef = it as? IrPropertyReference
                    ?: throw AssertionError("Property reference expected: ${it.render()}")
                val property = irPropertyRef.getter?.owner?.resolveFakeOverride()?.correspondingPropertySymbol?.owner
                    ?: throw AssertionError("isInitialized cannot be invoked on ${it.render()}")
                require(property.isLateinit) {
                    "isInitialized invoked on non-lateinit property ${property.render()}"
                }
                val backingField = property.backingField
                    ?: throw AssertionError("Lateinit property is supposed to have a backing field")
                transformBackingField(backingField, property)
                backendContext.createIrBuilder(it.symbol, expression.startOffset, expression.endOffset).run {
                    irNotEquals(
                        irGetField(it.dispatchReceiver, backingField),
                        irNull()
                    )
                }
            }
        }

        private fun transformBackingField(backingField: IrField, property: IrProperty) {
            assert(backingField.initializer == null) {
                "lateinit property backing field should not have an initializer:\n${property.dump()}"
            }
            backingField.type = backingField.type.makeNullable()
        }

        private fun transformGetter(backingField: IrField, getter: IrFunction) {
            val type = backingField.type
            assert(!type.isPrimitiveType()) {
                "'lateinit' property type should not be primitive:\n${backingField.dump()}"
            }
            val startOffset = getter.startOffset
            val endOffset = getter.endOffset
            getter.body = backendContext.irFactory.createBlockBody(startOffset, endOffset) {
                val irBuilder = backendContext.createIrBuilder(getter.symbol, startOffset, endOffset)
                irBuilder.run {
                    val resultVar = scope.createTmpVariable(
                        irGetField(getter.dispatchReceiverParameter?.let { irGet(it) }, backingField, type)
                    )
                    resultVar.parent = getter
                    statements.add(resultVar)
                    val throwIfNull = irIfThenElse(
                        context.irBuiltIns.nothingType,
                        irNotEquals(irGet(resultVar), irNull()),
                        irReturn(irGet(resultVar)),
                        throwUninitializedPropertyAccessException(backingField.name.asString())
                    )
                    statements.add(throwIfNull)
                }
            }
        }

        private fun IrBuilderWithScope.throwUninitializedPropertyAccessException(name: String) =
            backendContext.throwUninitializedPropertyAccessException(this, name)
    }
}

private inline fun IrExpression.replaceTailExpression(crossinline transform: (IrExpression) -> IrExpression): IrExpression {
    var current = this
    var block: IrContainerExpression? = null
    while (current is IrContainerExpression) {
        block = current
        current = current.statements.last() as IrExpression
    }
    current = transform(current)
    if (block == null) {
        return current
    }
    block.statements[block.statements.size - 1] = current
    return this
}
