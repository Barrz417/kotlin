// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// LANGUAGE: +InlineClasses
// DIAGNOSTICS: -INLINE_CLASS_DEPRECATED

import kotlin.reflect.KClass

inline class MyInt(val x: Int)
inline class MyString(val x: String)

annotation class Ann1(val a: <!INVALID_TYPE_OF_ANNOTATION_MEMBER!>MyInt<!>)
annotation class Ann2(val a: <!INVALID_TYPE_OF_ANNOTATION_MEMBER!>Array<MyString><!>)
annotation class Ann3(<!FORBIDDEN_VARARG_PARAMETER_TYPE!>vararg<!> val a: <!INVALID_TYPE_OF_ANNOTATION_MEMBER!>MyInt<!>)

annotation class Ann4(val a: KClass<MyInt>)

/* GENERATED_FIR_TAGS: annotationDeclaration, classDeclaration, outProjection, primaryConstructor, propertyDeclaration,
vararg */
