// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: k.kt

interface G<T>
interface SubG : G<String>

interface K {
    fun foo(): G<String>
    fun bar(): G<String>?
}

// FILE: J.java

import java.util.*;

interface J extends K {
    SubG foo();
    SubG bar();
    SubG baz();
}

/* GENERATED_FIR_TAGS: functionDeclaration, interfaceDeclaration, nullableType, typeParameter */
