/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.konan.file.File

/**
 * Reads entry points from this file and converts it to a predicate which would determine
 * whether a given declaration should be exposed.
 */
fun File.readObjCExportPredicate(): ObjCExportPredicate =
        readObjCEntryPointList()
                .toSet()
                .let { entryPointSet ->
                    object : ObjCExportPredicate {
                        override fun shouldBeExposed(descriptor: CallableMemberDescriptor): Boolean =
                                entryPointSet.contains(descriptor)
                    }
                }
