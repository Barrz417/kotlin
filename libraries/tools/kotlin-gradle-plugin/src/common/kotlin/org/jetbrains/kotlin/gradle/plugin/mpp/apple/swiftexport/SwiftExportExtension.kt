/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Project
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportApi
import javax.inject.Inject

@ExperimentalSwiftExportApi
abstract class SwiftExportExtension @Inject constructor(private val project: Project) {

    /**
     * Configure name of the swift export built from this project.
     */
    var name: String = project.name
}