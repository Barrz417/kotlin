// WITH_REFLECT
// TARGET_BACKEND: JVM_IR

// FILE: Java1.java
public class Java1 extends KotlinClass { }

// FILE: 1.kt
import java.util.ArrayList

class A : Java1()

open class KotlinClass : ArrayList<Int>()
