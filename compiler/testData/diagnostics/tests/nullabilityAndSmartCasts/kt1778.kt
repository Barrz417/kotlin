// RUN_PIPELINE_TILL: FRONTEND
// CHECK_TYPE
// WITH_EXTRA_CHECKERS
//KT-1778 Automatically cast error
package kt1778

import checkSubtype

fun main(args : Array<String>) {
    val x = checkSubtype<Any>(args[0])
    if(x is <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.CharSequence<!>) {
        if ("a" == x) <!DEBUG_INFO_SMARTCAST!>x<!>.length else <!DEBUG_INFO_SMARTCAST!>x<!>.length() // OK
        if ("a" == x || "b" == x) <!DEBUG_INFO_SMARTCAST!>x<!>.length else <!DEBUG_INFO_SMARTCAST!>x<!>.length() // <– THEN ERROR
        if ("a" == x && "a" == x) <!DEBUG_INFO_SMARTCAST!>x<!>.length else <!DEBUG_INFO_SMARTCAST!>x<!>.length() // <– ELSE ERROR
    }
}

/* GENERATED_FIR_TAGS: andExpression, classDeclaration, disjunctionExpression, equalityExpression,
funWithExtensionReceiver, functionDeclaration, functionalType, ifExpression, infix, integerLiteral, intersectionType,
isExpression, javaFunction, localProperty, nullableType, propertyDeclaration, smartcast, stringLiteral, typeParameter,
typeWithExtension */
