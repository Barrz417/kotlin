// RUN_PIPELINE_TILL: FRONTEND
fun <T> getT(): T = null!!

class Test<in I, out O> {
    private var i: I = getT()
    private val j: I

    init {
        j = getT()
        i = getT()
        this.i = getT()
    }

    fun test() {
        i = getT()
        this.i = getT()
        with(Test<I, O>()) {
            i = getT() // K1: this@Test.i, K2: this@with.i, see KT-55446
            this.<!INVISIBLE_MEMBER("i; private/*private to this*/; 'Test'")!>i<!> = getT()
            this@with.<!INVISIBLE_MEMBER("i; private/*private to this*/; 'Test'")!>i<!> = getT()
            this@Test.i  = getT()
        }
    }

    fun <I, O> test(t: Test<I, O>) {
        t.<!INVISIBLE_MEMBER("i; private/*private to this*/; 'Test'")!>i<!> = getT()
    }

    companion object {
        fun <I, O> test(t: Test<I, O>) {
            t.<!INVISIBLE_MEMBER("i; private/*private to this*/; 'Test'")!>i<!> = getT()
        }
    }
}

fun <I, O> test(t: Test<I, O>) {
    t.<!INVISIBLE_MEMBER("i; private/*private to this*/; 'Test'")!>i<!> = getT()
}

/* GENERATED_FIR_TAGS: assignment, checkNotNullCall, classDeclaration, companionObject, functionDeclaration, in, init,
lambdaLiteral, nullableType, objectDeclaration, out, propertyDeclaration, thisExpression, typeParameter */
