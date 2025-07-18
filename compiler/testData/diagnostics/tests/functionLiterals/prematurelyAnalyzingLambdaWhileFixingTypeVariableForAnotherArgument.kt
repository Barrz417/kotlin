// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER
// SKIP_TXT
// Issue: KT-35168

class Inv<T>

// Before the fix, here we had an exception while fixing a type variable for Inv due to prematurely analyzing a lambda
// The exception was: "UninitializedPropertyAccessException: lateinit property subResolvedAtoms has not been initialized"
class Foo<T>(x: (T) -> T, y: Inv<Any>)

fun bar() {
    Foo<Any>({}, Inv())
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, functionalType, lambdaLiteral, nullableType,
primaryConstructor, typeParameter */
