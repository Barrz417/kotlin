// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
internal abstract class Test</*0*/ in I> {
    private/*private to this*/ final fun foo(): I {
        throw Exception()
    }

    private/*private to this*/ final val i: I get() = foo()
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, getter, in, nullableType, propertyDeclaration,
typeParameter */
