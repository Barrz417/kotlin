// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class MyClass

// Correct at compile time but wrong at run-time
operator fun MyClass?.inc(): MyClass? { return null }

public fun box() {
    var i : MyClass? 
    i = MyClass()
    // type of j should be MyClass?
    var j = ++i
    // j is null so call should be unsafe
    j<!UNSAFE_CALL!>.<!>hashCode()
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, funWithExtensionReceiver, functionDeclaration,
incrementDecrementExpression, localProperty, nullableType, operator, propertyDeclaration, smartcast */
