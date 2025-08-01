// TODO: KT-37010 KT-37085
// WITH_STDLIB
// LANGUAGE: +JvmNullOutSpilledCoroutineLocals

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
class Controller {
    suspend fun suspendHere() = suspendCoroutineUninterceptedOrReturn<String> { x ->
        x.resume("OK")
    }

    suspend fun tailCall(): String {
        return suspendHere()
    }

    suspend fun nonTailCall(): String {
        suspendHere()

        return "OK"
    }

    suspend fun multipleSuspensions(): String {
        suspendHere()
        return suspendHere()
    }
}

fun builder(c: suspend Controller.() -> Unit) {

}

fun box(): String {
    var result = ""

    builder { ->
        val z = ""
        val u = 1L
        result = suspendHere()

        result += z + u
    }

    return result
}
