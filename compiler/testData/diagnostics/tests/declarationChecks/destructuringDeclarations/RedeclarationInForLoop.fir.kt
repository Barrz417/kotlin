// RUN_PIPELINE_TILL: FRONTEND
class A {
    operator fun component1() = 1
    operator fun component2() = 1
}

class C {
    operator fun iterator(): Iterator<A> = null!!
}

fun test() {
    for ((<!REDECLARATION!>x<!>, <!REDECLARATION!>x<!>) in C()) {

    }
}

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, forLoop, functionDeclaration, integerLiteral, localProperty,
operator, propertyDeclaration */
