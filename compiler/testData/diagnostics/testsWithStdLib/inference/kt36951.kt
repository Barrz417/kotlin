// RUN_PIPELINE_TILL: FRONTEND
// JAVAC_EXPECTED_FILE
// DIAGNOSTICS: -UNUSED_PARAMETER

class Base<T : <!CYCLIC_GENERIC_UPPER_BOUND!>T<!>> : HashSet<T>() {
    fun foo() {
        <!SUPER_CANT_BE_EXTENSION_RECEIVER!>super<!>.<!TYPE_INFERENCE_ONLY_INPUT_TYPES_ERROR!>remove<!>("")
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, stringLiteral, superExpression, typeConstraint,
typeParameter */
