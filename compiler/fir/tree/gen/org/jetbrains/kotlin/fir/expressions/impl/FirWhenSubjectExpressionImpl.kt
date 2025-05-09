/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/fir/tree/tree-generator/Readme.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package org.jetbrains.kotlin.fir.expressions.impl

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.MutableOrEmptyList
import org.jetbrains.kotlin.fir.builder.toMutableOrEmpty
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenSubjectExpression
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.fir.visitors.transformInplace

@OptIn(UnresolvedExpressionTypeAccess::class)
internal class FirWhenSubjectExpressionImpl(
    @property:UnresolvedExpressionTypeAccess
    override var coneTypeOrNull: ConeKotlinType?,
    override var annotations: MutableOrEmptyList<FirAnnotation>,
    override var source: KtSourceElement?,
    override var nonFatalDiagnostics: MutableOrEmptyList<ConeDiagnostic>,
    override var calleeReference: FirNamedReference,
) : FirWhenSubjectExpression() {
    override val contextArguments: List<FirExpression>
        get() = emptyList()
    override val typeArguments: List<FirTypeProjection>
        get() = emptyList()
    override val explicitReceiver: FirExpression?
        get() = null
    override val dispatchReceiver: FirExpression?
        get() = null
    override val extensionReceiver: FirExpression?
        get() = null

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        annotations.forEach { it.accept(visitor, data) }
        calleeReference.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirWhenSubjectExpressionImpl {
        transformAnnotations(transformer, data)
        transformCalleeReference(transformer, data)
        return this
    }

    override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D): FirWhenSubjectExpressionImpl {
        annotations.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformTypeArguments(transformer: FirTransformer<D>, data: D): FirWhenSubjectExpressionImpl {
        return this
    }

    override fun <D> transformExplicitReceiver(transformer: FirTransformer<D>, data: D): FirWhenSubjectExpressionImpl {
        return this
    }

    override fun <D> transformCalleeReference(transformer: FirTransformer<D>, data: D): FirWhenSubjectExpressionImpl {
        calleeReference = calleeReference.transform(transformer, data)
        return this
    }

    override fun replaceConeTypeOrNull(newConeTypeOrNull: ConeKotlinType?) {
        coneTypeOrNull = newConeTypeOrNull
    }

    override fun replaceAnnotations(newAnnotations: List<FirAnnotation>) {
        annotations = newAnnotations.toMutableOrEmpty()
    }

    override fun replaceContextArguments(newContextArguments: List<FirExpression>) {}

    override fun replaceTypeArguments(newTypeArguments: List<FirTypeProjection>) {}

    override fun replaceExplicitReceiver(newExplicitReceiver: FirExpression?) {}

    override fun replaceDispatchReceiver(newDispatchReceiver: FirExpression?) {}

    override fun replaceExtensionReceiver(newExtensionReceiver: FirExpression?) {}

    @FirImplementationDetail
    override fun replaceSource(newSource: KtSourceElement?) {
        source = newSource
    }

    override fun replaceNonFatalDiagnostics(newNonFatalDiagnostics: List<ConeDiagnostic>) {
        nonFatalDiagnostics = newNonFatalDiagnostics.toMutableOrEmpty()
    }

    override fun replaceCalleeReference(newCalleeReference: FirNamedReference) {
        calleeReference = newCalleeReference
    }

    override fun replaceCalleeReference(newCalleeReference: FirReference) {
        require(newCalleeReference is FirNamedReference)
        replaceCalleeReference(newCalleeReference)
    }
}
