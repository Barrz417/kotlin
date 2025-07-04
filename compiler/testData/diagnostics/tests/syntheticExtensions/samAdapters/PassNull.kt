// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: KotlinFile.kt
fun foo(javaInterface: JavaInterface) {
    javaInterface.doIt(null, null) { }
    javaInterface.doIt("", { }, null)
}

// FILE: JavaInterface.java
public interface JavaInterface {
    void doIt(String s, Runnable runnable1, Runnable runnable2);
}

/* GENERATED_FIR_TAGS: functionDeclaration, javaFunction, javaType, lambdaLiteral, nullableType, samConversion,
stringLiteral */
