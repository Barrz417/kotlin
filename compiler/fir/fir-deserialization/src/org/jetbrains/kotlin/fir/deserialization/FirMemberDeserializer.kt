/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.deserialization

import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusWithLazyEffectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildExpressionStub
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.transformers.setLazyPublishedVisibility
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.impl.FirImplicitUnitTypeRef
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.protobuf.MessageLite
import org.jetbrains.kotlin.serialization.deserialization.ProtoEnumFlags
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.serialization.deserialization.getName

class FirDeserializationContext(
    val nameResolver: NameResolver,
    val typeTable: TypeTable,
    val versionRequirementTable: VersionRequirementTable,
    val moduleData: FirModuleData,
    val packageFqName: FqName,
    val relativeClassName: FqName?,
    val typeDeserializer: FirTypeDeserializer,
    val annotationDeserializer: AbstractAnnotationDeserializer,
    val constDeserializer: FirConstDeserializer,
    val containerSource: DeserializedContainerSource?,
    val outerClassSymbol: FirRegularClassSymbol?,
    val outerTypeParameters: List<FirTypeParameterSymbol>
) {
    val session: FirSession = moduleData.session

    val allTypeParameters: List<FirTypeParameterSymbol> =
        typeDeserializer.ownTypeParameters + outerTypeParameters

    fun childContext(
        typeParameterProtos: List<ProtoBuf.TypeParameter>,
        containingDeclarationSymbol: FirBasedSymbol<*>,
        nameResolver: NameResolver = this.nameResolver,
        typeTable: TypeTable = this.typeTable,
        relativeClassName: FqName? = this.relativeClassName,
        containerSource: DeserializedContainerSource? = this.containerSource,
        outerClassSymbol: FirRegularClassSymbol? = this.outerClassSymbol,
        annotationDeserializer: AbstractAnnotationDeserializer = this.annotationDeserializer,
        constDeserializer: FirConstDeserializer = this.constDeserializer,
        capturesTypeParameters: Boolean = true,
    ): FirDeserializationContext = FirDeserializationContext(
        nameResolver,
        typeTable,
        versionRequirementTable,
        moduleData,
        packageFqName,
        relativeClassName,
        typeDeserializer.forChildContext(
            typeParameterProtos, containingDeclarationSymbol, nameResolver, typeTable, annotationDeserializer
        ),
        annotationDeserializer,
        constDeserializer,
        containerSource,
        outerClassSymbol,
        if (capturesTypeParameters) allTypeParameters else emptyList()
    )

    val memberDeserializer: FirMemberDeserializer = FirMemberDeserializer(this)
    val dispatchReceiver: ConeClassLikeType? =
        relativeClassName?.let { ClassId(packageFqName, it, isLocal = false).defaultType(allTypeParameters) }

    companion object {
        fun createForPackage(
            fqName: FqName,
            packageProto: ProtoBuf.Package,
            nameResolver: NameResolver,
            moduleData: FirModuleData,
            annotationDeserializer: AbstractAnnotationDeserializer,
            flexibleTypeFactory: FirTypeDeserializer.FlexibleTypeFactory,
            constDeserializer: FirConstDeserializer,
            containerSource: DeserializedContainerSource?
        ): FirDeserializationContext = createRootContext(
            nameResolver,
            TypeTable(packageProto.typeTable),
            moduleData,
            VersionRequirementTable.create(packageProto.versionRequirementTable),
            annotationDeserializer,
            flexibleTypeFactory,
            constDeserializer,
            fqName,
            relativeClassName = null,
            typeParameterProtos = emptyList(),
            containerSource,
            outerClassSymbol = null,
            outerClassEffectiveVisibility = EffectiveVisibility.Public,
            containingDeclarationSymbol = null
        )

        fun createForClass(
            classId: ClassId,
            classProto: ProtoBuf.Class,
            nameResolver: NameResolver,
            moduleData: FirModuleData,
            annotationDeserializer: AbstractAnnotationDeserializer,
            flexibleTypeFactory: FirTypeDeserializer.FlexibleTypeFactory,
            constDeserializer: FirConstDeserializer,
            containerSource: DeserializedContainerSource?,
            outerClassSymbol: FirRegularClassSymbol,
            outerClassEffectiveVisibility: EffectiveVisibility,
        ): FirDeserializationContext = createRootContext(
            nameResolver,
            TypeTable(classProto.typeTable),
            moduleData,
            VersionRequirementTable.create(classProto.versionRequirementTable),
            annotationDeserializer,
            flexibleTypeFactory,
            constDeserializer,
            classId.packageFqName,
            classId.relativeClassName,
            classProto.typeParameterList,
            containerSource,
            outerClassSymbol,
            outerClassEffectiveVisibility,
            outerClassSymbol
        )

        private fun createRootContext(
            nameResolver: NameResolver,
            typeTable: TypeTable,
            moduleData: FirModuleData,
            versionRequirementTable: VersionRequirementTable,
            annotationDeserializer: AbstractAnnotationDeserializer,
            flexibleTypeFactory: FirTypeDeserializer.FlexibleTypeFactory,
            constDeserializer: FirConstDeserializer,
            packageFqName: FqName,
            relativeClassName: FqName?,
            typeParameterProtos: List<ProtoBuf.TypeParameter>,
            containerSource: DeserializedContainerSource?,
            outerClassSymbol: FirRegularClassSymbol?,
            outerClassEffectiveVisibility: EffectiveVisibility,
            containingDeclarationSymbol: FirBasedSymbol<*>?
        ): FirDeserializationContext {
            return FirDeserializationContext(
                nameResolver, typeTable,
                versionRequirementTable,
                moduleData,
                packageFqName,
                relativeClassName,
                FirTypeDeserializer(
                    moduleData,
                    nameResolver,
                    typeTable,
                    annotationDeserializer,
                    flexibleTypeFactory,
                    typeParameterProtos,
                    null,
                    containingDeclarationSymbol
                ),
                annotationDeserializer,
                constDeserializer,
                containerSource,
                outerClassSymbol,
                emptyList()
            )
        }
    }
}

class FirNestedTypeAliasDeserializationContext(
    val memberDeserializer: FirMemberDeserializer,
    val proto: ProtoBuf.TypeAlias,
    val scopeProvider: FirScopeProvider,
)

class FirMemberDeserializer(private val c: FirDeserializationContext) {
    private val contractDeserializer = FirContractDeserializer(c)

    private fun loadOldFlags(oldFlags: Int): Int {
        val lowSixBits = oldFlags and 0x3f
        val rest = (oldFlags shr 8) shl 6
        return lowSixBits + rest
    }

    /**
     * If loading happens in post-compute, then symbol for type alias is already computed and should be provided in [preComputedSymbol]
     * @See AbstractFirDeserializedSymbolProvider.findAndDeserializeTypeAlias
     */
    fun loadTypeAlias(
        proto: ProtoBuf.TypeAlias,
        classId: ClassId,
        scopeProvider: FirScopeProvider,
        preComputedSymbol: FirTypeAliasSymbol? = null
    ): FirTypeAlias {
        val flags = proto.flags
        val symbol = preComputedSymbol ?: FirTypeAliasSymbol(classId)
        val local = c.childContext(proto.typeParameterList, containingDeclarationSymbol = symbol)
        val versionRequirements = VersionRequirement.create(proto, c)
        return buildTypeAlias {
            moduleData = c.moduleData
            origin = FirDeclarationOrigin.Library
            this.scopeProvider = scopeProvider
            this.name = classId.shortClassName
            val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(flags))
            status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(
                visibility,
                Modality.FINAL,
                visibility.toLazyEffectiveVisibility(owner = null),
            ).apply {
                isExpect = Flags.IS_EXPECT_CLASS.get(flags)
                isActual = false
            }

            annotations += c.annotationDeserializer.loadTypeAliasAnnotations(proto, local.nameResolver)
            this.symbol = symbol
            expandedTypeRef = proto.expandedType(c.typeTable).toTypeRef(local)
            resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
            typeParameters += local.typeDeserializer.ownTypeParameters.map { it.fir }
            deprecationsProvider = annotations.getDeprecationsProviderFromAnnotations(c.session, fromJava = false, versionRequirements)
        }.apply {
            this.versionRequirements = versionRequirements
            sourceElement = c.containerSource
            deserializeCompilerPluginMetadata(c, proto, ProtoBuf.TypeAlias::getCompilerPluginDataList)
        }
    }

    private fun loadPropertyGetter(
        proto: ProtoBuf.Property,
        classSymbol: FirClassSymbol<*>?,
        defaultAccessorFlags: Int,
        returnTypeRef: FirTypeRef,
        propertySymbol: FirPropertySymbol,
        local: FirDeserializationContext,
        propertyModality: Modality,
    ): FirPropertyAccessor {
        val getterFlags = if (proto.hasGetterFlags()) proto.getterFlags else defaultAccessorFlags
        val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(getterFlags))
        val accessorModality = ProtoEnumFlags.modality(Flags.MODALITY.get(getterFlags))
        val effectiveVisibility = visibility.toLazyEffectiveVisibility(classSymbol)
        return if (Flags.IS_NOT_DEFAULT.get(getterFlags)) {
            buildPropertyAccessor {
                moduleData = c.moduleData
                origin = FirDeclarationOrigin.Library
                this.returnTypeRef = returnTypeRef
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
                isGetter = true
                status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(visibility, accessorModality, effectiveVisibility).apply {
                    isInline = Flags.IS_INLINE_ACCESSOR.get(getterFlags)
                    isExternal = Flags.IS_EXTERNAL_ACCESSOR.get(getterFlags)
                }
                this.symbol = FirPropertyAccessorSymbol()
                dispatchReceiverType = c.dispatchReceiver
                this.propertySymbol = propertySymbol
            }.apply {
                this.versionRequirements = VersionRequirement.create(proto, c)
            }
        } else {
            FirDefaultPropertyGetter(
                source = null,
                c.moduleData,
                FirDeclarationOrigin.Library,
                returnTypeRef,
                propertySymbol,
                status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(visibility, propertyModality, effectiveVisibility),
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES,
            )
        }.apply {
            replaceAnnotations(
                c.annotationDeserializer.loadPropertyGetterAnnotations(
                    c.containerSource, proto, local.nameResolver, local.typeTable, getterFlags
                )
            )
            containingClassForStaticMemberAttr = c.dispatchReceiver?.lookupTag
        }
    }

    private fun loadPropertySetter(
        proto: ProtoBuf.Property,
        classProto: ProtoBuf.Class? = null,
        classSymbol: FirClassSymbol<*>?,
        defaultAccessorFlags: Int,
        returnTypeRef: FirTypeRef,
        propertySymbol: FirPropertySymbol,
        local: FirDeserializationContext,
        propertyModality: Modality,
    ): FirPropertyAccessor {
        val setterFlags = if (proto.hasSetterFlags()) proto.setterFlags else defaultAccessorFlags
        val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(setterFlags))
        val accessorModality = ProtoEnumFlags.modality(Flags.MODALITY.get(setterFlags))
        val effectiveVisibility = visibility.toLazyEffectiveVisibility(classSymbol)
        return if (Flags.IS_NOT_DEFAULT.get(setterFlags)) {
            buildPropertyAccessor {
                moduleData = c.moduleData
                origin = FirDeclarationOrigin.Library
                this.returnTypeRef = FirImplicitUnitTypeRef(source)
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
                isGetter = false
                status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(visibility, accessorModality, effectiveVisibility).apply {
                    isInline = Flags.IS_INLINE_ACCESSOR.get(setterFlags)
                    isExternal = Flags.IS_EXTERNAL_ACCESSOR.get(setterFlags)
                }
                this.symbol = FirPropertyAccessorSymbol()
                dispatchReceiverType = c.dispatchReceiver
                local.memberDeserializer.addValueParametersTo(
                    listOf(proto.setterValueParameter),
                    symbol,
                    proto,
                    AbstractAnnotationDeserializer.CallableKind.PROPERTY_SETTER,
                    classProto,
                    kind = FirValueParameterKind.Regular,
                    destination = valueParameters,
                )
                this.propertySymbol = propertySymbol
            }.apply {
                this.versionRequirements = VersionRequirement.create(proto, c)
            }
        } else {
            FirDefaultPropertySetter(
                source = null,
                c.moduleData,
                FirDeclarationOrigin.Library,
                returnTypeRef,
                propertySymbol,
                status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(visibility, propertyModality, effectiveVisibility),
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES,
            )
        }.apply {
            replaceAnnotations(
                c.annotationDeserializer.loadPropertySetterAnnotations(
                    c.containerSource, proto, local.nameResolver, local.typeTable, setterFlags
                )
            )
            containingClassForStaticMemberAttr = c.dispatchReceiver?.lookupTag
        }
    }

    fun loadProperty(
        proto: ProtoBuf.Property,
        classProto: ProtoBuf.Class? = null,
        classSymbol: FirClassSymbol<*>? = null
    ): FirProperty {
        val flags = if (proto.hasFlags()) proto.flags else loadOldFlags(proto.oldFlags)
        val callableName = c.nameResolver.getName(proto.name)
        val callableId = CallableId(c.packageFqName, c.relativeClassName, callableName)
        val symbol = FirRegularPropertySymbol(callableId)
        val local = c.childContext(proto.typeParameterList, containingDeclarationSymbol = symbol)

        // Per documentation on Property.getter_flags in metadata.proto, if an accessor flags field is absent, its value should be computed
        // by taking hasAnnotations/visibility/modality from property flags, and using false for the rest
        val defaultAccessorFlags = Flags.getAccessorFlags(
            Flags.HAS_ANNOTATIONS.get(flags),
            Flags.VISIBILITY.get(flags),
            Flags.MODALITY.get(flags),
            false, false, false
        )

        val returnTypeRef = proto.returnType(c.typeTable).toTypeRef(local)

        val hasGetter = Flags.HAS_GETTER.get(flags)
        val receiverAnnotations = if (hasGetter && proto.hasReceiver()) {
            c.annotationDeserializer.loadExtensionReceiverParameterAnnotations(
                c.containerSource, proto, local.nameResolver, local.typeTable, AbstractAnnotationDeserializer.CallableKind.PROPERTY_GETTER
            )
        } else {
            emptyList()
        }

        val propertyModality = ProtoEnumFlags.modality(Flags.MODALITY.get(flags))

        val isVar = Flags.IS_VAR.get(flags)
        val versionRequirements = VersionRequirement.create(proto, c)

        // classSymbol?.classKind?.isAnnotationClass throws 'Fir is not initialized for FirRegularClassSymbol kotlin/String'
        val isFromAnnotation = classProto != null && Flags.CLASS_KIND.get(classProto.flags) == ProtoBuf.Class.Kind.ANNOTATION_CLASS
        return buildProperty {
            moduleData = c.moduleData
            origin = FirDeclarationOrigin.Library
            this.returnTypeRef = returnTypeRef
            receiverParameter = proto.receiverType(c.typeTable)?.toTypeRef(local)?.let { receiverType ->
                buildReceiverParameter {
                    typeRef = receiverType
                    annotations += receiverAnnotations
                    this.symbol = FirReceiverParameterSymbol()
                    this.moduleData = c.moduleData
                    this.origin = FirDeclarationOrigin.Library
                    containingDeclarationSymbol = symbol
                }
            }

            name = callableName
            this.isVar = isVar
            this.symbol = symbol
            dispatchReceiverType = c.dispatchReceiver
            val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(flags))
            status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(
                visibility,
                propertyModality,
                visibility.toLazyEffectiveVisibility(classSymbol)
            ).apply {
                isExpect = Flags.IS_EXPECT_PROPERTY.get(flags)
                isActual = false
                isOverride = false
                isConst = Flags.IS_CONST.get(flags)
                isLateInit = Flags.IS_LATEINIT.get(flags)
                isExternal = Flags.IS_EXTERNAL_PROPERTY.get(flags)
                hasMustUseReturnValue = Flags.HAS_MUST_USE_RETURN_VALUE_PROPERTY.get(flags)
            }

            resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
            typeParameters += local.typeDeserializer.ownTypeParameters.map { it.fir }
            annotations +=
                c.annotationDeserializer.loadPropertyAnnotations(c.containerSource, proto, classProto, local.nameResolver, local.typeTable)
            val backingFieldAnnotations = mutableListOf<FirAnnotation>()
            backingFieldAnnotations +=
                c.annotationDeserializer.loadPropertyBackingFieldAnnotations(
                    c.containerSource, proto, local.nameResolver, local.typeTable
                )
            backingFieldAnnotations +=
                c.annotationDeserializer.loadPropertyDelegatedFieldAnnotations(
                    c.containerSource, proto, local.nameResolver, local.typeTable
                )
            backingField = FirDefaultPropertyBackingField(
                c.moduleData,
                FirDeclarationOrigin.Library,
                source = null,
                backingFieldAnnotations,
                returnTypeRef,
                isVar,
                symbol,
                status,
            )

            if (hasGetter) {
                this.getter = loadPropertyGetter(
                    proto,
                    classSymbol,
                    defaultAccessorFlags,
                    returnTypeRef,
                    symbol,
                    local,
                    propertyModality
                )
            }
            if (Flags.HAS_SETTER.get(flags)) {
                this.setter = loadPropertySetter(
                    proto,
                    classProto,
                    classSymbol,
                    defaultAccessorFlags,
                    returnTypeRef,
                    symbol,
                    local,
                    propertyModality
                )
            }
            this.containerSource = c.containerSource
            this.initializer = when {
                Flags.HAS_CONSTANT.get(proto.flags) -> {
                    c.constDeserializer.loadConstant(
                        proto, symbol.callableId, c.nameResolver, returnTypeRef.coneType.isUnsignedTypeOrNullableUnsignedType
                    )
                }

                isFromAnnotation -> {
                    c.annotationDeserializer.loadAnnotationPropertyDefaultValue(
                        c.containerSource,
                        proto,
                        returnTypeRef,
                        local.nameResolver,
                        local.typeTable
                    )
                }
                else -> null
            }

            local.memberDeserializer.loadContextParametersTo(
                proto.contextParameterList,
                proto.contextReceiverTypes(c.typeTable),
                symbol,
                proto,
                callableKind = AbstractAnnotationDeserializer.CallableKind.PROPERTY_GETTER,
                classProto,
                deserializationOrigin = FirDeclarationOrigin.Library,
                destination = contextParameters,
            )
        }.apply {
            when (val initializer = initializer) {
                /**
                 * Deserialized annotation values don't have any information about type arguments for annotation expression, so
                 *   they should be updated from the expected type
                 *
                 * ```
                 * annotation class One<T>(val s: String)
                 *
                 * annotation class Two(
                 *     val one: One<Int> = One("hello") // <--------
                 * )
                 * ```
                 *
                 * Annotation value for `One("hello")` contains only annotation type (`One`) and argument values (`"hello"`)
                 */
                is FirAnnotation -> initializer.replaceAnnotationTypeRef(initializer.annotationTypeRef.withReplacedReturnType(returnTypeRef.coneType))
                else -> initializer?.replaceConeTypeOrNull(returnTypeRef.coneType)
            }
            this.versionRequirements = versionRequirements
            c.session.deserializationExtension?.loadHasBackingFieldFlag(proto)?.let { hasBackingField ->
                @OptIn(FirImplementationDetail::class)
                hasBackingFieldAttr = hasBackingField
            }

            if (Flags.IS_DELEGATED[proto.flags]) {
                @OptIn(FirImplementationDetail::class)
                isDelegatedPropertyAttr = true
            }

            if (isFromAnnotation) {
                isDeserializedPropertyFromAnnotation = true
            }

            replaceDeprecationsProvider(getDeprecationsProvider(c.session))
            deserializeCompilerPluginMetadata(c, proto, ProtoBuf.Property::getCompilerPluginDataList)
            setLazyPublishedVisibility(c.session)
            getter?.setLazyPublishedVisibility(annotations, this, c.session)
            setter?.setLazyPublishedVisibility(annotations, this, c.session)
        }
    }

    private fun loadContextParametersTo(
        contextParameterList: MutableList<ProtoBuf.ValueParameter>,
        legacyContextReceiverTypes: List<ProtoBuf.Type>,
        symbol: FirBasedSymbol<*>,
        proto: MessageLite,
        callableKind: AbstractAnnotationDeserializer.CallableKind,
        classProto: ProtoBuf.Class?,
        deserializationOrigin: FirDeclarationOrigin,
        destination: MutableList<FirValueParameter>,
    ) {
        if (contextParameterList.isNotEmpty()) {
            addValueParametersTo(
                contextParameterList,
                symbol,
                proto,
                callableKind,
                classProto,
                FirValueParameterKind.ContextParameter,
                destination = destination
            )
        } else {
            legacyContextReceiverTypes.mapTo(destination) {
                loadLegacyContextReceiver(it, deserializationOrigin, symbol)
            }
        }
    }

    fun loadLegacyContextReceiver(
        proto: ProtoBuf.Type,
        origin: FirDeclarationOrigin,
        containingDeclarationSymbol: FirBasedSymbol<*>,
    ): FirValueParameter {
        val typeRef = proto.toTypeRef(c)
        return buildValueParameter {
            this.moduleData = c.moduleData
            this.origin = origin
            this.name = SpecialNames.UNDERSCORE_FOR_UNUSED_VAR
            this.symbol = FirValueParameterSymbol()
            this.returnTypeRef = typeRef
            this.containingDeclarationSymbol = containingDeclarationSymbol
            this.valueParameterKind = FirValueParameterKind.LegacyContextReceiver
            resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
        }
    }

    internal fun createContextParametersForClass(
        classProto: ProtoBuf.Class,
        origin: FirDeclarationOrigin,
        containingDeclarationSymbol: FirBasedSymbol<*>,
    ): List<FirValueParameter> =
        classProto.contextReceiverTypes(c.typeTable).map { loadLegacyContextReceiver(it, origin, containingDeclarationSymbol) }

    fun loadFunction(
        proto: ProtoBuf.Function,
        classProto: ProtoBuf.Class? = null,
        classSymbol: FirClassSymbol<*>? = null,
        // TODO: introduce the similar changes for the other deserialized entities
        deserializationOrigin: FirDeclarationOrigin = FirDeclarationOrigin.Library
    ): FirSimpleFunction {
        val flags = if (proto.hasFlags()) proto.flags else loadOldFlags(proto.oldFlags)

        val receiverAnnotations = if (proto.hasReceiver()) {
            c.annotationDeserializer.loadExtensionReceiverParameterAnnotations(
                c.containerSource, proto, c.nameResolver, c.typeTable, AbstractAnnotationDeserializer.CallableKind.OTHERS
            )
        } else {
            emptyList()
        }

        val callableName = c.nameResolver.getName(proto.name)
        val callableId = CallableId(c.packageFqName, c.relativeClassName, callableName)
        val symbol = FirNamedFunctionSymbol(callableId)
        val local = c.childContext(proto.typeParameterList, containingDeclarationSymbol = symbol)

        val versionRequirements = VersionRequirement.create(proto, c)
        val simpleFunction = buildSimpleFunction {
            moduleData = c.moduleData
            origin = deserializationOrigin
            returnTypeRef = proto.returnType(local.typeTable).toTypeRef(local)
            receiverParameter = proto.receiverType(local.typeTable)?.toTypeRef(local)?.let { receiverType ->
                buildReceiverParameter {
                    typeRef = receiverType
                    annotations += receiverAnnotations
                    this.symbol = FirReceiverParameterSymbol()
                    this.moduleData = c.moduleData
                    this.origin = deserializationOrigin
                    containingDeclarationSymbol = symbol
                }
            }

            name = callableName
            val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(flags))
            status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(
                visibility,
                ProtoEnumFlags.modality(Flags.MODALITY.get(flags)),
                visibility.toLazyEffectiveVisibility(classSymbol)
            ).apply {
                isExpect = Flags.IS_EXPECT_FUNCTION.get(flags)
                isActual = false
                isOverride = false
                isOperator = Flags.IS_OPERATOR.get(flags)
                isInfix = Flags.IS_INFIX.get(flags)
                isInline = Flags.IS_INLINE.get(flags)
                isTailRec = Flags.IS_TAILREC.get(flags)
                isExternal = Flags.IS_EXTERNAL_FUNCTION.get(flags)
                isSuspend = Flags.IS_SUSPEND.get(flags)
                hasStableParameterNames = !Flags.IS_FUNCTION_WITH_NON_STABLE_PARAMETER_NAMES.get(flags)
                hasMustUseReturnValue = Flags.HAS_MUST_USE_RETURN_VALUE_FUNCTION.get(flags)
            }
            this.symbol = symbol
            dispatchReceiverType = c.dispatchReceiver
            resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
            typeParameters += local.typeDeserializer.ownTypeParameters.map { it.fir }
            local.memberDeserializer.addValueParametersTo(
                proto.valueParameterList,
                symbol,
                proto,
                AbstractAnnotationDeserializer.CallableKind.OTHERS,
                classProto,
                kind = FirValueParameterKind.Regular,
                destination = valueParameters,
            )
            annotations +=
                c.annotationDeserializer.loadFunctionAnnotations(c.containerSource, proto, local.nameResolver, local.typeTable)
            deprecationsProvider = annotations.getDeprecationsProviderFromAnnotations(c.session, fromJava = false, versionRequirements)
            this.containerSource = c.containerSource

            local.memberDeserializer.loadContextParametersTo(
                proto.contextParameterList,
                proto.contextReceiverTypes(c.typeTable),
                symbol,
                proto,
                callableKind = AbstractAnnotationDeserializer.CallableKind.OTHERS,
                classProto,
                deserializationOrigin,
                destination = contextParameters,
            )
        }.apply {
            this.versionRequirements = versionRequirements
            setLazyPublishedVisibility(c.session)
            deserializeCompilerPluginMetadata(c, proto, ProtoBuf.Function::getCompilerPluginDataList)
        }
        if (proto.hasContract()) {
            val contractDeserializer = if (proto.typeParameterList.isEmpty()) this.contractDeserializer else FirContractDeserializer(local)
            val contractDescription = contractDeserializer.loadContract(proto.contract, simpleFunction)
            if (contractDescription != null) {
                simpleFunction.replaceContractDescription(contractDescription)
            }
        }
        return simpleFunction
    }

    fun loadConstructor(
        proto: ProtoBuf.Constructor,
        classProto: ProtoBuf.Class,
        classBuilder: FirRegularClassBuilder
    ): FirConstructor {
        val flags = proto.flags
        val relativeClassName = c.relativeClassName!!
        val callableId = CallableId(c.packageFqName, relativeClassName, relativeClassName.shortName())
        val symbol = FirConstructorSymbol(callableId)
        val local = c.childContext(emptyList(), containingDeclarationSymbol = symbol)
        val isPrimary = !Flags.IS_SECONDARY.get(flags)

        val typeParameters = classBuilder.typeParameters

        val delegatedSelfType = buildResolvedTypeRef {
            coneType = ConeClassLikeTypeImpl(
                classBuilder.symbol.toLookupTag(),
                typeParameters.map { ConeTypeParameterTypeImpl(it.symbol.toLookupTag(), false) }.toTypedArray(),
                false
            )
        }

        return if (isPrimary) {
            FirPrimaryConstructorBuilder()
        } else {
            FirConstructorBuilder()
        }.apply {
            moduleData = c.moduleData
            origin = FirDeclarationOrigin.Library
            returnTypeRef = delegatedSelfType
            val visibility = ProtoEnumFlags.visibility(Flags.VISIBILITY.get(flags))
            val isInner = classBuilder.status.isInner
            status = FirResolvedDeclarationStatusWithLazyEffectiveVisibility(
                visibility,
                Modality.FINAL,
                visibility.toLazyEffectiveVisibility(classBuilder.symbol)
            ).apply {
                // We don't store information about expect modifier on constructors
                // It is inherited from containing class
                isExpect = Flags.IS_EXPECT_CLASS.get(classProto.flags)
                hasStableParameterNames = !Flags.IS_CONSTRUCTOR_WITH_NON_STABLE_PARAMETER_NAMES.get(flags)
                isActual = false
                isOverride = false
                this.isInner = isInner
                hasMustUseReturnValue = Flags.HAS_MUST_USE_RETURN_VALUE_CTOR.get(flags)
            }
            this.symbol = symbol
            dispatchReceiverType =
                if (!isInner) null
                else with(c) {
                    ClassId(packageFqName, relativeClassName.parent(), isLocal = false).defaultType(outerTypeParameters)
                }
            resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
            this.typeParameters +=
                typeParameters.filterIsInstance<FirTypeParameter>()
                    .map { buildConstructedClassTypeParameterRef { this.symbol = it.symbol } }
            local.memberDeserializer.addValueParametersTo(
                proto.valueParameterList,
                symbol,
                proto,
                AbstractAnnotationDeserializer.CallableKind.OTHERS,
                classProto,
                kind = FirValueParameterKind.Regular,
                addDefaultValue = classBuilder.symbol.classId == StandardClassIds.Enum,
                destination = valueParameters,
            )
            annotations +=
                c.annotationDeserializer.loadConstructorAnnotations(c.containerSource, proto, local.nameResolver, local.typeTable)
            containerSource = c.containerSource
            deprecationsProvider = annotations.getDeprecationsProviderFromAnnotations(c.session, fromJava = false)

            classProto.contextReceiverTypes(c.typeTable).mapTo(contextParameters) { loadLegacyContextReceiver(it, FirDeclarationOrigin.Library, symbol) }
        }.build().apply {
            containingClassForStaticMemberAttr = c.dispatchReceiver!!.lookupTag
            this.versionRequirements = VersionRequirement.create(proto, c)
            deserializeCompilerPluginMetadata(c, proto, ProtoBuf.Constructor::getCompilerPluginDataList)
            setLazyPublishedVisibility(c.session)
        }
    }

    private fun defaultValue(flags: Int): FirExpression? {
        if (Flags.DECLARES_DEFAULT_VALUE.get(flags)) {
            return buildExpressionStub()
        }
        return null
    }

    private fun addValueParametersTo(
        valueParameters: List<ProtoBuf.ValueParameter>,
        containingDeclarationSymbol: FirBasedSymbol<*>,
        callableProto: MessageLite,
        callableKind: AbstractAnnotationDeserializer.CallableKind,
        classProto: ProtoBuf.Class?,
        kind: FirValueParameterKind,
        addDefaultValue: Boolean = false,
        destination: MutableList<FirValueParameter>,
    ) {
        valueParameters.mapIndexedTo(destination) { index, proto ->
            val flags = if (proto.hasFlags()) proto.flags else 0
            val name = if (kind == FirValueParameterKind.ContextParameter && !proto.hasName())
                SpecialNames.UNDERSCORE_FOR_UNUSED_VAR
            else
                c.nameResolver.getName(proto.name)
            buildValueParameter {
                moduleData = c.moduleData
                this.containingDeclarationSymbol = containingDeclarationSymbol
                origin = FirDeclarationOrigin.Library
                returnTypeRef = proto.type(c.typeTable).toTypeRef(c)
                this.name = name
                symbol = FirValueParameterSymbol()
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
                defaultValue = defaultValue(flags)
                if (addDefaultValue) {
                    defaultValue = buildExpressionStub()
                }
                isCrossinline = Flags.IS_CROSSINLINE.get(flags)
                isNoinline = Flags.IS_NOINLINE.get(flags)
                isVararg = proto.varargElementType(c.typeTable) != null
                annotations += c.annotationDeserializer.loadValueParameterAnnotations(
                    c.containerSource,
                    callableProto,
                    proto,
                    classProto,
                    c.nameResolver,
                    c.typeTable,
                    callableKind,
                    index,
                )
                valueParameterKind = kind
            }
        }
    }

    private fun ProtoBuf.Type.toTypeRef(context: FirDeserializationContext): FirResolvedTypeRef =
        context.typeDeserializer.typeRef(this)

    private fun Visibility.toLazyEffectiveVisibility(owner: FirClassLikeSymbol<*>?): Lazy<EffectiveVisibility> {
        return this.toLazyEffectiveVisibility(owner, c.session, forClass = false)
    }
}

fun Visibility.toLazyEffectiveVisibility(
    owner: FirClassLikeSymbol<*>?,
    session: FirSession,
    forClass: Boolean
): Lazy<EffectiveVisibility> {
    /*
     * `lowerBound` operation for `EffectiveVisibility.Protected` involves subtyping between container classes.
     * In some cases, during deserialization, this subtyping might lead to the infinite recursion.
     * Consider the following example:
     *
     * ```
     * class Outer {
     *     protected class Inner(protected val x: Any)
     * }
     * ```
     *
     * Here `Inner` class has effective visibility `protected (in Outer)` and `x` has `protected (in Inner)`.
     * So to perform the `lowerBound` operation between these two visibilities, the compiler needs to check the
     * subtyping between the `Outer` and `Inner`. BUT this happens during the deserialization in the following chain:
     * `deserialize Outer -> deserialize Inner -> deserialize x`, and no class symbols are published yet (neither
     * FIR element for them is created). So when subtyping tries to access supertypes of any of these classes, it triggers
     * deserialization once again which leads to stack overflow eventually.
     *
     * Due to this situation, we cannot compute the effective visibility eagerly, so we postpone its computation
     */
    return lazy(LazyThreadSafetyMode.PUBLICATION) l@{
        val selfEffectiveVisibility = this.toEffectiveVisibility(owner, forClass = forClass)
        val parentEffectiveVisibility = owner?.effectiveVisibility ?: EffectiveVisibility.Public
        parentEffectiveVisibility.lowerBound(selfEffectiveVisibility, session.typeContext)
    }
}
