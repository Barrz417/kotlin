// RUN_PIPELINE_TILL: FRONTEND
fun <T : Number> materializeNumber(): T = TODO()

fun a(): Unit = run {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>materializeNumber<!>()
}

fun b(): Unit = run {
    run {
        <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>materializeNumber<!>()
    }
}

/* GENERATED_FIR_TAGS: functionDeclaration, lambdaLiteral, typeConstraint, typeParameter */
