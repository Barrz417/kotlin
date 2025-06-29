// RUN_PIPELINE_TILL: FRONTEND
// FILE: A.java
public class A {
    public static void take(A[] array) {}
}

// FILE: B.java
public class B extends A {}

// FILE: main.kt

fun takeA(array: Array<A>) {}
fun takeOutA(array: Array<out A>) {}

fun test(array: Array<B>) {
    A.take(array)
    takeA(<!ARGUMENT_TYPE_MISMATCH!>array<!>)
    takeOutA(array)
}

/* GENERATED_FIR_TAGS: functionDeclaration, javaFunction, javaType, outProjection */
