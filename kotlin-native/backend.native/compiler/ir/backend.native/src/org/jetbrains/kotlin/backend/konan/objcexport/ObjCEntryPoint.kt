/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.backend.konan.objcexport.ObjCEntryPoint.Kind.*
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

data class ObjCEntryPoint(val kind: Kind, val pattern: Pattern) {
    /** A kind of an entry point */
    enum class Kind {
        /** A function. */
        FUNCTION,

        /** A property. */
        PROPERTY,

        /** A callable: function or property. */
        CALLABLE,

        /** Any member. */
        MEMBER,
    }

    /** A pattern which matches fully-qualified name. */
    data class Pattern(val path: List<String>, val name: Name) {
        /** The last component of a pattern. */
        sealed class Name {
            /** Matches explicit name. */
            data class Explicit(val string: String) : Name()

            /** Matches any name. */
            data object Wildcard : Name()
        }
    }
}

val ObjCEntryPoint.Kind.parentOrNull: ObjCEntryPoint.Kind?
    get() =
        when (this) {
            FUNCTION -> CALLABLE
            PROPERTY -> CALLABLE
            CALLABLE -> MEMBER
            MEMBER -> null
        }

val DeclarationDescriptor.objCEntryPointKind: ObjCEntryPoint.Kind
    get() = when (this) {
        is FunctionDescriptor -> FUNCTION
        is PropertyDescriptor -> PROPERTY
        is CallableDescriptor -> CALLABLE
        else -> MEMBER
    }

fun File.readObjCEntryPointSet(): Set<ObjCEntryPoint> =
        readStrings()
                .asSequence()
                .map { it.trim() }
                .filter { !it.startsWith("//") }
                .filter { it.isNotBlank() }
                .map { it.toObjCEntryPoint() }
                .toSet()

fun String.toObjCEntryPointKind(): ObjCEntryPoint.Kind =
        when (this) {
            "function" -> FUNCTION
            "property" -> PROPERTY
            "callable" -> CALLABLE
            "member" -> MEMBER
            else -> throw IllegalArgumentException("invalid kind: $this, should be: class or function")
        }

fun String.toObjCEntryPointPattern(): ObjCEntryPoint.Pattern =
        split('.').let { components ->
            ObjCEntryPoint.Pattern(
                    components.dropLast(1),
                    components.lastOrNull()
                            .let { it ?: throw IllegalArgumentException("invalid pattern: \"$this\", should be non-empty") }
                            .toObjCEntryPointPatternName())
        }

fun String.toObjCEntryPointPatternName(): ObjCEntryPoint.Pattern.Name =
        when (this) {
            "*" -> ObjCEntryPoint.Pattern.Name.Wildcard
            else -> ObjCEntryPoint.Pattern.Name.Explicit(this)
        }

fun String.toObjCEntryPoint(): ObjCEntryPoint =
        split(' ')
                .also { if (it.size != 2) throw IllegalArgumentException("invalid entry point: \"$this\", should match: \"<kind> <pattern>\"") }
                .let { ObjCEntryPoint(it[0].toObjCEntryPointKind(), it[1].toObjCEntryPointPattern()) }

fun FqName.toObjCPatternPath(): List<String> =
        pathSegments().map { it.asString() }

fun FqName.toObjCExplicitPattern(): ObjCEntryPoint.Pattern =
        ObjCEntryPoint.Pattern(
                parent().toObjCPatternPath(),
                ObjCEntryPoint.Pattern.Name.Explicit(shortName().asString()))

fun FqName.toObjCWildcardPattern(): ObjCEntryPoint.Pattern =
        ObjCEntryPoint.Pattern(
                parent().toObjCPatternPath(),
                ObjCEntryPoint.Pattern.Name.Wildcard)

fun Set<ObjCEntryPoint>.contains(kind: ObjCEntryPoint.Kind, fqName: FqName): Boolean =
        contains(ObjCEntryPoint(kind, fqName.toObjCExplicitPattern())) ||
                contains(ObjCEntryPoint(kind, fqName.toObjCWildcardPattern())) ||
                kind.parentOrNull?.let { contains(it, fqName) }.let { it ?: false }

fun Set<ObjCEntryPoint>.contains(descriptor: DeclarationDescriptor): Boolean {
    val kind = descriptor.objCEntryPointKind
    val fqName = descriptor.fqNameSafe
    return contains(kind, fqName)
}
