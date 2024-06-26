/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor

/** A predicate which checks whether the given declaration should be exposed in Objective-C. */
interface ObjCExportPredicate {
    fun shouldBeExposed(descriptor: CallableMemberDescriptor) = true

    companion object {
        val ALL: ObjCExportPredicate = object : ObjCExportPredicate {}
    }
}
