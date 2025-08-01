/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.codegen

import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.backend.common.lower.ANNOTATION_IMPLEMENTATION
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.backend.jvm.codegen.AnnotationCodegen.Companion.annotationClass
import org.jetbrains.kotlin.backend.jvm.extensions.descriptorOrigin
import org.jetbrains.kotlin.backend.jvm.ir.*
import org.jetbrains.kotlin.backend.jvm.mapping.IrTypeMapper
import org.jetbrains.kotlin.backend.jvm.mapping.MethodSignatureMapper
import org.jetbrains.kotlin.backend.jvm.mapping.mapClass
import org.jetbrains.kotlin.backend.jvm.mapping.mapType
import org.jetbrains.kotlin.backend.jvm.metadata.MetadataSerializer
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap.mapKotlinToJava
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.VersionIndependentOpcodes
import org.jetbrains.kotlin.codegen.addRecordComponent
import org.jetbrains.kotlin.codegen.inline.*
import org.jetbrains.kotlin.codegen.signature.JvmSignatureWriter
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.JvmBackendConfig
import org.jetbrains.kotlin.codegen.writeKotlinMetadata
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.PsiSourceManager
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.METADATA_JVM_IR_FLAG
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.METADATA_JVM_IR_STABLE_ABI_FLAG
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.load.kotlin.internalName
import org.jetbrains.kotlin.metadata.jvm.deserialization.BitEncoding
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMemberSignature
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_RECORD_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.JvmStandardClassIds.TRANSIENT_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.JvmStandardClassIds.VOLATILE_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmConstants
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmClassSignature
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.commons.Method
import java.io.File

class ClassCodegen private constructor(
    val irClass: IrClass,
    val context: JvmBackendContext,
    private val parentFunction: IrFunction?,
    val intrinsicExtensions: List<JvmIrIntrinsicExtension>,
) {
    // We need to avoid recursive calls to getOrCreate() from within the constructor to prevent lockups
    // in ConcurrentHashMap context.classCodegens.
    private val parentClassCodegen by lazy {
        (parentFunction?.parentAsClass ?: irClass.parent as? IrClass)?.let { getOrCreate(it, context, intrinsicExtensions) }
    }
    private val metadataSerializer: MetadataSerializer by lazy {
        context.backendExtension.createSerializer(
            context, irClass, type, visitor.serializationBindings, parentClassCodegen?.metadataSerializer
        )
    }

    val smap = context.getSourceMapper(irClass)
    private val state: GenerationState get() = context.state
    private val config: JvmBackendConfig = context.config

    private val innerClasses = mutableSetOf<IrClass>()
    val typeMapper = object : IrTypeMapper(context) {
        override fun mapType(type: IrType, mode: TypeMappingMode, sw: JvmSignatureWriter?, materialized: Boolean): Type {
            var t = type
            while (t.isArray()) {
                t = t.getArrayElementType(context.irBuiltIns)
            }
            // Only record inner class info for types that are materialized in the class file.
            if (materialized) {
                t.classOrNull?.owner?.let(::addInnerClassInfo)
            }
            return super.mapType(type, mode, sw, materialized)
        }
    }

    val methodSignatureMapper = MethodSignatureMapper(context, typeMapper)

    val type: Type = typeMapper.mapClass(irClass)

    val reifiedTypeParametersUsages = ReifiedTypeParametersUsages()

    private val jvmMethodSignatureClashDetector = JvmMethodSignatureClashDetector(this)
    private val jvmFieldSignatureClashDetector = JvmFieldSignatureClashDetector(this)

    private val visitor = state.factory.newVisitor(irClass.descriptorOrigin, type, irClass.fileParent.loadSourceFilesInfo()).apply {
        val signature = typeMapper.mapClassSignature(irClass, type, state.classBuilderMode.generateBodies)
        // Ensure that the backend only produces class names that would be valid in the frontend for JVM.
        if (state.classBuilderMode.generateBodies && signature.hasInvalidName()) {
            throw IllegalStateException("Generating class with invalid name '${type.className}': ${irClass.dump()}")
        }
        defineClass(
            irClass.psiElement,
            config.classFileVersion,
            irClass.getFlags(config.languageVersionSettings),
            signature.name,
            signature.javaGenericSignature,
            signature.superclassName,
            signature.interfaces.toTypedArray()
        )
    }

    // TODO: the names produced by generators in this map depend on the order in which methods are generated; see above.
    private val regeneratedObjectNameGenerators = mutableMapOf<String, NameGenerator>()

    fun getRegeneratedObjectNameGenerator(function: IrFunction): NameGenerator {
        val name = if (function.name.isSpecial) SPECIAL_TRANSFORMATION_NAME else "\$${function.name.asString()}"
        return regeneratedObjectNameGenerators.getOrPut(name) {
            NameGenerator("${type.internalName}$name$INLINE_CALL_TRANSFORMATION_SUFFIX")
        }
    }

    private var generated = false

    fun generate() {
        // TODO: reject repeated generate() calls; currently, these can happen for objects in finally
        //       blocks since they are `accept`ed once per each CFG edge out of the try-finally.
        if (generated) return
        generated = true

        // Respect the [GenerateClassFilter] installed in the generation state
        if (shouldSkipCodeGenerationAccordingToGenerationFilter()) return

        // Generate PermittedSubclasses attribute for sealed class.
        if (config.languageVersionSettings.supportsFeature(LanguageFeature.JvmPermittedSubclassesAttributeForSealed) &&
            irClass.modality == Modality.SEALED &&
            config.target >= JvmTarget.JVM_17
        ) {
            generatePermittedSubclasses()
        }

        // Generating a method node may cause the addition of a field with an initializer if an inline function
        // call uses `assert` and the JVM assertions mode is enabled. To avoid concurrent modification errors,
        // there is a very specific generation order.
        // 1. Any method other than `<clinit>` can add a field and a `<clinit>` statement:
        for (method in irClass.declarations.filterIsInstance<IrFunction>()) {
            if (method.name.asString() != "<clinit>" &&
                method.origin != IrDeclarationOrigin.INLINE_LAMBDA &&
                method.origin != IrDeclarationOrigin.ADAPTER_FOR_FUN_INTERFACE_CONSTRUCTOR &&
                !(method.origin == IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE && method.body == null)
            ) {
                generateMethod(method, smap)
            }
        }
        // 2. `<clinit>` itself can add a field, but the statement is generated via the `return init` hack:
        irClass.functions.find { it.name.asString() == "<clinit>" }?.let { generateMethod(it, smap) }
        // 3. Now we have all the fields, including `$assertionsDisabled` if needed:
        for (field in irClass.fields) {
            generateField(field)
        }
        // 4. Generate nested classes at the end, to ensure that when the companion's metadata is serialized
        //    everything moved to the outer class has already been recorded in `globalSerializationBindings`.
        for (declaration in irClass.declarations) {
            if (declaration is IrClass) {
                getOrCreate(declaration, context, intrinsicExtensions).generate()
            }
        }

        generateAnnotations()

        visitor.visitSMAP(
            smap,
            !config.languageVersionSettings.supportsFeature(LanguageFeature.CorrectSourceMappingSyntax),
            parentFunction != null && parentFunction.isInline
        )

        reifiedTypeParametersUsages.mergeAll(irClass.reifiedTypeParameters)

        generateInnerAndOuterClasses()

        visitor.done(config.generateSmapCopyToAnnotation)
        jvmMethodSignatureClashDetector.reportErrorsTo(context.ktDiagnosticReporter)
        jvmFieldSignatureClashDetector.reportErrorsTo(context.ktDiagnosticReporter)
    }

    private fun shouldSkipCodeGenerationAccordingToGenerationFilter(): Boolean {
        val filter = state.generateDeclaredClassFilter
        val ktFile = PsiSourceManager.findPsiElement(irClass, irClass, KtFile::class)
        val ktClass = PsiSourceManager.findPsiElement(irClass, irClass, KtClassOrObject::class)
        return (ktFile != null && filter != null && !filter.shouldGeneratePackagePart(ktFile))
                || (ktClass != null && filter != null && !filter.shouldGenerateClass(ktClass))
    }

    private fun generatePermittedSubclasses() {
        val sealedSubclasses = irClass.sealedSubclasses
        if (sealedSubclasses.isEmpty()) return
        val classVisitor = visitor.visitor
        for (sealedSubclassSymbol in sealedSubclasses) {
            classVisitor.visitPermittedSubclass(typeMapper.mapClass(sealedSubclassSymbol.owner).internalName)
        }
    }

    fun generateAssertFieldIfNeeded(generatingClInit: Boolean): IrExpression? {
        if (irClass.hasAssertionsDisabledField(context))
            return null
        val topLevelClass = generateSequence(this) { it.parentClassCodegen }.last().irClass
        val field = irClass.buildAssertionsDisabledField(context, topLevelClass)
        irClass.declarations.add(0, field)
        // Normally, `InitializersLowering` would move the initializer to <clinit>, but it's obviously too late for that.
        val init = with(field) {
            IrSetFieldImpl(startOffset, endOffset, symbol, null, initializer!!.expression, context.irBuiltIns.unitType)
        }
        if (generatingClInit) {
            // Too late to modify the IR; have to ask the currently active `ExpressionCodegen` to generate this statement
            // directly. At least we know that nothing before this point uses the field.
            return init
        }
        val classInitializer = irClass.functions.singleOrNull { it.name.asString() == "<clinit>" } ?: irClass.addFunction {
            name = Name.special("<clinit>")
            returnType = context.irBuiltIns.unitType
        }.apply {
            body = context.irFactory.createBlockBody(startOffset, endOffset)
        }
        // Should be initialized first in case some inline function call in `<clinit>` also uses assertions.
        (classInitializer.body as IrBlockBody).statements.add(0, init)
        return null
    }

    private fun generateAnnotations() {
        class Codegen(private val superInterfaceIndex: Int) : AnnotationCodegen(this@ClassCodegen) {
            override fun visitAnnotation(descr: String, visible: Boolean): AnnotationVisitor {
                return visitor.visitor.visitAnnotation(descr, visible)
            }

            override fun visitTypeAnnotation(descr: String, path: TypePath?, visible: Boolean): AnnotationVisitor {
                val typeReference = TypeReference.newSuperTypeReference(superInterfaceIndex)
                return visitor.visitor.visitTypeAnnotation(typeReference.value, path, descr, visible)
            }
        }

        Codegen(-1).genAnnotations(irClass)

        var superInterfaceIndex = 0
        for (supertype in irClass.superTypes) {
            val codegen = Codegen(if (supertype.isInterface()) superInterfaceIndex++ else -1)
            codegen.generateTypeAnnotations(supertype, TypeAnnotationPosition.Supertype)
        }

        AnnotationCodegen.genAnnotationsOnTypeParametersAndBounds(
            context,
            irClass,
            this,
            TypeReference.CLASS_TYPE_PARAMETER,
            TypeReference.CLASS_TYPE_PARAMETER_BOUND
        ) { typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean ->
            visitor.visitor.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
        }

        generateKotlinMetadataAnnotation()
    }

    private fun generateKotlinMetadataAnnotation() {
        val facadeClassName = irClass.multifileFacadeForPart
        val metadata = irClass.metadata
        val entry = irClass.fileParent.fileEntry
        val kind = when {
            facadeClassName != null -> KotlinClassHeader.Kind.MULTIFILE_CLASS_PART
            metadata is MetadataSource.Class -> KotlinClassHeader.Kind.CLASS
            metadata is MetadataSource.Script -> KotlinClassHeader.Kind.CLASS
            metadata is MetadataSource.ReplSnippet -> KotlinClassHeader.Kind.CLASS
            metadata is MetadataSource.File -> KotlinClassHeader.Kind.FILE_FACADE
            metadata is MetadataSource.Function -> KotlinClassHeader.Kind.SYNTHETIC_CLASS
            entry is MultifileFacadeFileEntry -> KotlinClassHeader.Kind.MULTIFILE_CLASS
            else -> KotlinClassHeader.Kind.SYNTHETIC_CLASS
        }
        val serializedIr = when (metadata) {
            is MetadataSource.Class -> metadata.serializedIr
            is MetadataSource.File -> metadata.serializedIr
            else -> null
        }

        val isMultifileClassOrPart = kind == KotlinClassHeader.Kind.MULTIFILE_CLASS || kind == KotlinClassHeader.Kind.MULTIFILE_CLASS_PART

        var extraFlags = METADATA_JVM_IR_FLAG or
                (if (config.abiStability != JvmAbiStability.UNSTABLE) METADATA_JVM_IR_STABLE_ABI_FLAG else 0)
        if (isMultifileClassOrPart && config.languageVersionSettings.getFlag(JvmAnalysisFlags.inheritMultifileParts)) {
            extraFlags = extraFlags or JvmAnnotationNames.METADATA_MULTIFILE_PARTS_INHERIT_FLAG
        }
        if (metadata is MetadataSource.Script) {
            extraFlags = extraFlags or JvmAnnotationNames.METADATA_SCRIPT_FLAG
        }

        // There are four kinds of classes which are regenerated during inlining.
        // 1) Anonymous classes which are in the scope of an inline function.
        // 2) SAM wrappers used in an inline function. These are identified by name, since they
        //    can be reused in different functions and are thus generated in the enclosing top-level
        //    class instead of inside of an inline function.
        // 3) WhenMapping classes used from public inline functions. These are collected in
        //    `JvmBackendContext.publicAbiSymbols` in `MappedEnumWhenLowering`.
        // 4) Annotation implementation classes used from public inline function. Similar to
        //    public WhenMapping classes, these are collected in `publicAbiSymbols` in
        //    `JvmAnnotationImplementationTransformer`.
        val isPublicAbi = irClass.isPublicAbi || irClass.isInlineSamWrapper ||
                type.isAnonymousClass && irClass.isInPublicInlineScope

        writeKotlinMetadata(visitor, context.config, kind, isPublicAbi, extraFlags) { av ->
            if (metadata != null) {
                val containingFile = when (val containingFileMetadata = irClass.file.metadata) {
                    is MetadataSource.File -> containingFileMetadata
                    is MetadataSource.CodeFragment -> null
                    else -> error("Cannot serialize class metadata without containing file: ${irClass.render()}")
                }
                metadataSerializer.serialize(metadata, containingFile)?.let { (proto, stringTable) ->
                    AsmUtil.writeAnnotationData(
                        av, JvmProtoBufUtil.writeData(proto, stringTable), ArrayUtil.toStringArray(stringTable.strings),
                    )
                }
            }

            if (entry is MultifileFacadeFileEntry) {
                val arv = av.visitArray(JvmAnnotationNames.METADATA_DATA_FIELD_NAME)
                for (partFile in entry.partFiles) {
                    val fileClass = partFile.declarations.singleOrNull { it.isFileClass } as IrClass?
                    if (fileClass != null) arv.visit(null, typeMapper.mapClass(fileClass).internalName)
                }
                arv.visitEnd()
            }

            if (facadeClassName != null) {
                av.visit(JvmAnnotationNames.METADATA_MULTIFILE_CLASS_NAME_FIELD_NAME, facadeClassName.internalName)
            }

            if (irClass.classNameOverride != null) {
                val isFileClass = isMultifileClassOrPart || kind == KotlinClassHeader.Kind.FILE_FACADE
                assert(isFileClass) { "JvmPackageName is not supported for classes: ${irClass.render()}" }
                av.visit(JvmAnnotationNames.METADATA_PACKAGE_NAME_FIELD_NAME, irClass.fqNameWhenAvailable!!.parent().asString())
            }
        }
        serializedIr?.let { storeSerializedIr(it) }
    }

    private fun IrFile.loadSourceFilesInfo(): List<File> {
        val entry = fileEntry
        if (entry is MultifileFacadeFileEntry) {
            return entry.partFiles.flatMap { it.loadSourceFilesInfo() }
        }
        return listOf(File(entry.name))
    }

    private fun generateField(field: IrField) {
        val fieldType = typeMapper.mapType(field)
        val fieldSignature =
            if (field.origin == IrDeclarationOrigin.PROPERTY_DELEGATE) null
            else methodSignatureMapper.mapFieldSignature(field)
        val fieldName = field.name.asString()
        val flags = field.computeFieldFlags(context, config.languageVersionSettings)
        val fv = visitor.newField(
            field.descriptorOrigin, flags, fieldName, fieldType.descriptor,
            fieldSignature, (field.initializer?.expression as? IrConst)?.value
        )

        jvmFieldSignatureClashDetector.trackDeclaration(field, JvmMemberSignature.Field(fieldName, fieldType.descriptor))

        if (field.origin != JvmLoweredDeclarationOrigin.CONTINUATION_CLASS_RESULT_FIELD) {
            val annotationCodegen = object : AnnotationCodegen(this@ClassCodegen) {
                override fun visitAnnotation(descr: String, visible: Boolean): AnnotationVisitor {
                    return fv.visitAnnotation(descr, visible)
                }

                override fun visitTypeAnnotation(descr: String, path: TypePath?, visible: Boolean): AnnotationVisitor {
                    return fv.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).value, path, descr, visible)
                }
            }
            annotationCodegen.genAnnotations(field)
            if (!AsmUtil.isPrimitive(fieldType) &&
                flags and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_ENUM) == 0 &&
                (field.origin != IrDeclarationOrigin.FIELD_FOR_OBJECT_INSTANCE || !irClass.isSyntheticSingleton)
            ) {
                annotationCodegen.generateNullabilityAnnotation(field)
            }
            annotationCodegen.generateTypeAnnotations(field.type, TypeAnnotationPosition.FieldType(field))
        }

        (field.metadata as? MetadataSource.Property)?.let {
            metadataSerializer.bindFieldMetadata(it, fieldType to fieldName)
        }

        if (irClass.hasAnnotation(JVM_RECORD_ANNOTATION_FQ_NAME) && !field.isStatic) {
            val rcv = visitor.addRecordComponent(fieldName, fieldType.descriptor, fieldSignature)
            val annotationCodegen = object : AnnotationCodegen(this@ClassCodegen) {
                override fun visitAnnotation(descr: String, visible: Boolean): AnnotationVisitor {
                    return rcv.visitAnnotation(descr, visible)
                }
            }
            val recordAnnotations = field.annotations.filter { annotation ->
                val applicableTargets = annotation.annotationClass.applicableJavaTargetSet()
                applicableTargets == null || "RECORD_COMPONENT" in applicableTargets
            }
            annotationCodegen.genAnnotations(field, recordAnnotations)
        }
    }

    private val generatedInlineMethods = mutableMapOf<IrFunction, SMAPAndMethodNode>()

    fun generateMethodNode(method: IrFunction): SMAPAndMethodNode {
        if (!method.isInline && !method.isSuspendCapturingCrossinline()) {
            // Inline methods can be used multiple times by `IrSourceCompilerForInline`, suspend methods
            // are used twice (`f` and `f$$forInline`) if they capture crossinline lambdas, and everything
            // else is only generated by `generateMethod` below so does not need caching.
            // TODO: inline lambdas are not marked `isInline`, and are generally used once, but may be needed
            //       multiple times if declared in a `finally` block - should they be cached?
            return FunctionCodegen(method, this).generate()
        }

        // Only allow generation of one inline method at a time, to avoid deadlocks when files call inline methods of each other.
        val (node, smap) =
            generatedInlineMethods[method] ?: synchronized(context.inlineMethodGenerationLock) {
                generatedInlineMethods.getOrPut(method) { FunctionCodegen(method, this).generate() }
            }
        return SMAPAndMethodNode(cloneMethodNode(node), smap)
    }

    private fun generateMethod(method: IrFunction, classSMAP: SourceMapper) {
        if (method.isFakeOverride) {
            jvmMethodSignatureClashDetector.trackFakeOverrideMethod(method)
            return
        }

        val (node, smap) = generateMethodNode(method)
        node.preprocessSuspendMarkers(
            method.origin == JvmLoweredDeclarationOrigin.FOR_INLINE_STATE_MACHINE_TEMPLATE || method.isEffectivelyInlineOnly(),
            method.origin == JvmLoweredDeclarationOrigin.FOR_INLINE_STATE_MACHINE_TEMPLATE_CAPTURES_CROSSINLINE
        )
        val mv = with(node) { visitor.newMethod(method.descriptorOrigin, access, name, desc, signature, exceptions.toTypedArray()) }
        val smapCopyingVisitor = SourceMapCopyingMethodVisitor(classSMAP, smap, mv)

        if (method.hasContinuation()) {
            // Generate a state machine within this method. The continuation class for it should be generated
            // lazily so that if tail call optimization kicks in, the unused class will not be written to the output.
            val continuationClass = method.continuationClass() // null if `SuspendLambda.invokeSuspend` - `this` is continuation itself
            val continuationClassCodegen =
                lazy { if (continuationClass != null) getOrCreate(continuationClass, context, intrinsicExtensions, method) else this }

            // For suspend lambdas continuation class is null, and we need to use containing class to put L$ fields
            val spilledFieldsOwner = continuationClass ?: irClass

            node.acceptWithStateMachine(
                method,
                this,
                smapCopyingVisitor,
                spilledFieldsOwner.continuationClassVarsCountByType ?: emptyMap()
            ) {
                continuationClassCodegen.value.visitor
            }

            if (continuationClass != null && (continuationClassCodegen.isInitialized() || method.isSuspendCapturingCrossinline())) {
                continuationClassCodegen.value.generate()
            }
        } else {
            node.accept(smapCopyingVisitor)
        }
        jvmMethodSignatureClashDetector.trackDeclaration(method, JvmMemberSignature.Method(node.name, node.desc))

        when (val metadata = method.metadata) {
            is MetadataSource.Property -> metadataSerializer.bindPropertyMetadata(metadata, Method(node.name, node.desc), method.origin)
            is MetadataSource.Function -> metadataSerializer.bindMethodMetadata(metadata, Method(node.name, node.desc))
            null -> Unit
            else -> error("Incorrect metadata source $metadata for:\n${method.dump()}")
        }
    }

    private fun generateInnerAndOuterClasses() {
        // JVMS7 (4.7.6): a nested class or interface member will have InnerClasses information
        // for each enclosing class and for each immediate member
        parentClassCodegen?.innerClasses?.add(irClass)
        for (codegen in generateSequence(this) { it.parentClassCodegen }.takeWhile { it.parentClassCodegen != null }) {
            innerClasses.add(codegen.irClass)
        }

        // JVMS7 (4.7.7): A class must have an EnclosingMethod attribute if and only if
        // it is a local class or an anonymous class.
        //
        // The attribute contains the innermost class that encloses the declaration of
        // the current class. If the current class is immediately enclosed by a method
        // or constructor, the name and type of the function is recorded as well.
        if (parentClassCodegen != null) {
            // In case there's no primary constructor, it's unclear which constructor should be the enclosing one, so we select the first.
            val enclosingFunction = if (irClass.isEnclosedInConstructor) {
                val containerClass = parentClassCodegen!!.irClass
                containerClass.primaryConstructor
                    ?: containerClass.declarations.firstIsInstanceOrNull<IrConstructor>()
                    ?: error("Class in a non-static initializer found, but container has no constructors: ${containerClass.render()}")
            } else parentFunction
            if (enclosingFunction != null || irClass.isAnonymousInnerClass) {
                val method = enclosingFunction?.let(methodSignatureMapper::mapAsmMethod)?.takeIf { it.name != "<clinit>" }
                visitor.visitOuterClass(parentClassCodegen!!.type.internalName, method?.name, method?.descriptor)
            }
        }

        for (klass in innerClasses.sortedBy { it.fqNameWhenAvailable?.asString() }) {
            val innerJavaClassId = klass.mapToJava()
            val innerClass = innerJavaClassId?.internalName ?: typeMapper.classLikeDeclarationInternalName(klass)
            val outerClass =
                if (klass.isSamWrapper || klass.isAnnotationImplementation || klass.isEnclosedInConstructor)
                    null
                else {
                    when (val parent = klass.parent) {
                        is IrClass -> {
                            val outerJavaClassId = parent.mapToJava()
                            if (innerJavaClassId?.packageFqName != outerJavaClassId?.packageFqName) {
                                continue
                            }
                            outerJavaClassId?.internalName ?: typeMapper.classLikeDeclarationInternalName(parent)
                        }

                        else -> null
                    }
                }
            val innerName = if (klass.isAnonymousInnerClass) null else innerJavaClassId?.shortClassName?.asString() ?: klass.name.asString()
            val accessFlags = klass.calculateInnerClassAccessFlags(context)
            visitor.visitInnerClass(innerClass, outerClass, innerName, accessFlags)
        }
    }

    private fun IrClass.mapToJava(): ClassId? = fqNameWhenAvailable?.toUnsafe()?.let(::mapKotlinToJava)

    private val IrClass.isAnonymousInnerClass: Boolean
        get() = isSamWrapper || name.isSpecial || isAnnotationImplementation // NB '<Continuation>' is treated as anonymous inner class here

    private val IrClass.isInlineSamWrapper: Boolean
        get() = isSamWrapper && visibility == DescriptorVisibilities.PUBLIC

    private val IrClass.isSamWrapper: Boolean
        get() = origin == IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION

    private val IrClass.isAnnotationImplementation: Boolean
        get() = origin == ANNOTATION_IMPLEMENTATION

    fun addInnerClassInfo(innerClass: IrClass) {
        var c = innerClass
        while (!c.isTopLevelDeclaration && innerClasses.add(c)) {
            c = c.parentClassOrNull ?: break
        }
    }

    private fun storeSerializedIr(serializedIr: ByteArray) {
        val av = visitor.newAnnotation(JvmAnnotationNames.SERIALIZED_IR_DESC, true)
        val partsVisitor = av.visitArray(JvmAnnotationNames.SERIALIZED_IR_BYTES_FIELD_NAME)
        val serializedIrParts = BitEncoding.encodeBytes(serializedIr)
        for (part in serializedIrParts) {
            partsVisitor.visit(null, part)
        }
        partsVisitor.visitEnd()
        av.visitEnd()
    }

    companion object {
        private var IrClass.classCodegen: ClassCodegen? by irAttribute(copyByDefault = false)

        fun getOrCreate(
            irClass: IrClass,
            context: JvmBackendContext,
            intrinsicExtensions: List<JvmIrIntrinsicExtension>,
            // The `parentFunction` is only set for classes nested inside of functions. This is usually safe, since there is no
            // way to refer to (inline) members of such a class from outside of the function unless the function in question is
            // itself declared as inline. In that case, the function will be compiled before we can refer to the nested class.
            //
            // The one exception to this rule are anonymous objects defined as members of a class. These are nested inside of the
            // class initializer, but can be referred to from anywhere within the scope of the class. That's why we have to ensure
            // that all references to classes inside of <clinit> have a non-null `parentFunction`.
            parentFunction: IrFunction? = (irClass.parent as? IrFunction)?.takeIf {
                it.origin == JvmLoweredDeclarationOrigin.CLASS_STATIC_INITIALIZER
            },
        ): ClassCodegen =
            irClass.classCodegen ?: ClassCodegen(irClass, context, parentFunction, intrinsicExtensions).also {
                assert(parentFunction == null || it.parentFunction == parentFunction) {
                    "inconsistent parent function for ${irClass.render()}:\n" +
                            "New: ${parentFunction!!.render()}\n" +
                            "Old: ${it.parentFunction?.render()}"
                }
                irClass.classCodegen = it
            }

        private fun JvmClassSignature.hasInvalidName() =
            name.splitToSequence('/').any { identifier -> identifier.any { it in JvmConstants.INVALID_CHARS } }
    }
}

private fun IrClass.getFlags(languageVersionSettings: LanguageVersionSettings): Int =
    origin.flags or
            getVisibilityAccessFlagForClass() or
            (if (isAnnotatedWithDeprecated) Opcodes.ACC_DEPRECATED else 0) or
            getSynthAccessFlag(languageVersionSettings) or
            when {
                isAnnotationClass -> Opcodes.ACC_ANNOTATION or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
                isInterface -> Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
                isEnumClass -> Opcodes.ACC_ENUM or Opcodes.ACC_SUPER or modality.flags
                hasAnnotation(JVM_RECORD_ANNOTATION_FQ_NAME) -> VersionIndependentOpcodes.ACC_RECORD or Opcodes.ACC_SUPER or modality.flags
                else -> Opcodes.ACC_SUPER or modality.flags
            }

private fun IrClass.getSynthAccessFlag(languageVersionSettings: LanguageVersionSettings): Int {
    if (hasAnnotation(JVM_SYNTHETIC_ANNOTATION_FQ_NAME))
        return Opcodes.ACC_SYNTHETIC
    if (origin == IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION &&
        languageVersionSettings.supportsFeature(LanguageFeature.SamWrapperClassesAreSynthetic)
    )
        return Opcodes.ACC_SYNTHETIC
    return 0
}

private fun IrField.computeFieldFlags(context: JvmBackendContext, languageVersionSettings: LanguageVersionSettings): Int =
    origin.flags or visibility.flags or
            (if (isDeprecatedCallable(context) ||
                correspondingPropertySymbol?.owner?.isDeprecatedCallable(context) == true
            ) Opcodes.ACC_DEPRECATED else 0) or
            (if (isFinal) Opcodes.ACC_FINAL else 0) or
            (if (isStatic) Opcodes.ACC_STATIC else 0) or
            (if (hasAnnotation(VOLATILE_ANNOTATION_FQ_NAME)) Opcodes.ACC_VOLATILE else 0) or
            (if (hasAnnotation(TRANSIENT_ANNOTATION_FQ_NAME)) Opcodes.ACC_TRANSIENT else 0) or
            (if (hasAnnotation(JVM_SYNTHETIC_ANNOTATION_FQ_NAME) ||
                isPrivateCompanionFieldInInterface(languageVersionSettings)
            ) Opcodes.ACC_SYNTHETIC else 0)

private fun IrField.isPrivateCompanionFieldInInterface(languageVersionSettings: LanguageVersionSettings): Boolean =
    origin == IrDeclarationOrigin.FIELD_FOR_OBJECT_INSTANCE &&
            languageVersionSettings.supportsFeature(LanguageFeature.ProperVisibilityForCompanionObjectInstanceField) &&
            parentAsClass.isJvmInterface &&
            DescriptorVisibilities.isPrivate(parentAsClass.companionObject()!!.visibility)

private val IrDeclarationOrigin.flags: Int
    get() = (if (isSynthetic) Opcodes.ACC_SYNTHETIC else 0) or
            (if (this == IrDeclarationOrigin.FIELD_FOR_ENUM_ENTRY) Opcodes.ACC_ENUM else 0)

private val Modality.flags: Int
    get() = when (this) {
        Modality.ABSTRACT, Modality.SEALED -> Opcodes.ACC_ABSTRACT
        Modality.FINAL -> Opcodes.ACC_FINAL
        Modality.OPEN -> 0
    }

private val DescriptorVisibility.flags: Int
    get() = AsmUtil.getVisibilityAccessFlag(delegate) ?: throw AssertionError("Unsupported visibility $this")

// From `isAnonymousClass` in inlineCodegenUtils.kt
private val Type.isAnonymousClass: Boolean
    get() = internalName.substringAfterLast("$", "").toIntOrNull() != null
