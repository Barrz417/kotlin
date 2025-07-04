// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE -UNCHECKED_CAST
// SKIP_TXT
// Issue: KT-20849

fun <T>test_1(x: T): T = null as T
fun <T>test_2(x: () -> T): T = null as T

fun case_1() {
    null?.run { return }
    null!!.<!UNREACHABLE_CODE!>run { throw Exception() }<!>
}

fun case_2() {
    test_1 { null!! }
    test_2 { null!! }
}

fun case_3() {
    test_1 { throw Exception() }
    test_2 { throw Exception() }
}

fun case_6() {
    null!!
}

fun case_7(x: Boolean?) {
    <!DEBUG_INFO_IMPLICIT_EXHAUSTIVE!>when (x) {
        true -> throw Exception()
        false -> throw Exception()
        null -> throw Exception()
    }<!>
}

fun <T> something(): T = Any() as T

class Context<T>

fun <T> Any.decodeIn(typeFrom: Context<in T>): T = something()

fun <T> Any?.decodeOut1(typeFrom: Context<out T>): T {
    return <!TYPE_MISMATCH!>this?.<!IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION!>decodeIn<!>(typeFrom) ?: kotlin.Unit<!>
}

fun <T> Any.decodeOut2(typeFrom: Context<out T>): T {
    <!UNREACHABLE_CODE!>val x: Nothing =<!> this.decodeIn(typeFrom)
}

fun <T> Any.decodeOut3(typeFrom: Context<out T>): T {
    <!UNREACHABLE_CODE!>val x =<!> this.<!IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION!>decodeIn<!>(typeFrom)
}

fun <T> Any.decodeOut4(typeFrom: Context<out T>): T {
    <!UNREACHABLE_CODE!>val x: Any =<!> this.<!IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION!>decodeIn<!>(typeFrom)
}

class TrieNode<out E> {
    companion object {
        internal val EMPTY = TrieNode<Nothing>()
    }
}
class PersistentHashSet<out E>(root: TrieNode<E>) {
    companion object {
        internal val EMPTY = PersistentHashSet(TrieNode.EMPTY)
    }
}

interface F<in T>
fun <T> F<T>.join() = {}

fun main() {
    val f: Any = Any()
    (f as F<*>).join()
}

fun bug(worker: Worker<Unit>) {
    stateless<Boolean, Nothing, Unit> {
        onWorkerOutput(worker)
    }
}

fun <StateT, OutputT : Any, T> RenderContext<StateT, OutputT>.onWorkerOutput(worker: Worker<T>): Unit = Unit

fun <InputT, OutputT : Any, RenderingT> stateless(
    render: RenderContext<Nothing, OutputT>.(input: InputT) -> RenderingT
) { }

interface Worker<out T>

interface RenderContext<StateT, in OutputT : Any>

val emptyOrNull: List<Nothing>? = null
val x = emptyOrNull?.get(0)

val errorCompletion = { e: Throwable -> throw Exception() }

fun test1() {
    errorCompletion(Exception("fail"))
}
fun test2() {
    errorCompletion.invoke(Exception("fail"))
}

fun test3() {
    foo {
        produceNothing()
    }
}

fun produceNothing(): Nothing = TODO()

fun <R> foo(block: suspend String.() -> R) = null as R

/* GENERATED_FIR_TAGS: asExpression, checkNotNullCall, classDeclaration, companionObject, elvisExpression,
equalityExpression, funWithExtensionReceiver, functionDeclaration, functionalType, in, inProjection, integerLiteral,
interfaceDeclaration, lambdaLiteral, localProperty, nullableType, objectDeclaration, out, outProjection,
primaryConstructor, propertyDeclaration, safeCall, smartcast, starProjection, stringLiteral, suspend, thisExpression,
typeConstraint, typeParameter, typeWithExtension, whenExpression, whenWithSubject */
