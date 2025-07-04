// RUN_PIPELINE_TILL: BACKEND
// OPT_IN: kotlin.RequiresOptIn
// FILE: api.kt

package api

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalAPI

@ExperimentalAPI
const val MEANING = 42

annotation class Anno(val value: Int)

// FILE: usage-propagate.kt

package usage1

import api.*

@ExperimentalAPI
@Anno(MEANING)
fun usage() {}

// FILE: usage-use.kt

@file:OptIn(ExperimentalAPI::class)
package usage2

import api.*

// TODO: there should be no warning here
@Anno(<!OPT_IN_USAGE!>MEANING<!>)
fun usage() {}

// FILE: usage-none.kt

package usage3

import api.*

@Anno(<!OPT_IN_USAGE!>MEANING<!>)
fun usage() {}

/* GENERATED_FIR_TAGS: annotationDeclaration, annotationUseSiteTargetFile, classReference, const, functionDeclaration,
integerLiteral, primaryConstructor, propertyDeclaration */
