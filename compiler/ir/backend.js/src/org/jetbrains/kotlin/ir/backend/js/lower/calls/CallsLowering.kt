/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.calls

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.JsStandardClassIds

class CallsLowering(val context: JsIrBackendContext) : BodyLoweringPass {
    private val transformers = listOf(
        NumberOperatorCallsTransformer(context),
        NumberConversionCallsTransformer(context),
        BoxedLongCallsTransformer(context),
        EqualityAndComparisonCallsTransformer(context),
        PrimitiveContainerMemberCallTransformer(context),
        MethodsOfAnyCallsTransformer(context),
        ReflectionCallsTransformer(context),
        EnumIntrinsicsTransformer(context),
        ExceptionHelperCallsTransformer(context),
        BuiltInConstructorCalls(context),
        JsonIntrinsics(context),
        NativeGetterSetterTransformer(context),
    )

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildren(object : IrTransformer<IrDeclaration>() {
            override fun visitFunction(declaration: IrFunction, data: IrDeclaration): IrStatement {
                return super.visitFunction(declaration, declaration)
            }

            override fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: IrDeclaration): IrElement {
                val call = super.visitFunctionAccess(expression, data)
                val doNotIntrinsify = data.hasAnnotation(JsStandardClassIds.Annotations.DoNotIntrinsify)
                if (call is IrFunctionAccessExpression) {
                    for (transformer in transformers) {
                        val newCall = transformer.transformFunctionAccess(call, doNotIntrinsify)
                        if (newCall !== call) {
                            return newCall
                        }
                    }
                }
                return call
            }

            override fun visitFieldAccess(expression: IrFieldAccessExpression, data: IrDeclaration): IrExpression {
                val access = super.visitFieldAccess(expression, data)
                if (access is IrFieldAccessExpression) {
                    for (transformer in transformers) {
                        val newAccess = transformer.transformFieldAccess(access)
                        if (newAccess !== access) {
                            return newAccess
                        }
                    }
                }
                return access
            }
        }, container)
    }
}

interface CallsTransformer {
    fun transformFunctionAccess(call: IrFunctionAccessExpression, doNotIntrinsify: Boolean): IrExpression
    fun transformFieldAccess(access: IrFieldAccessExpression): IrExpression = access
}
