// RUN_PIPELINE_TILL: FRONTEND
val foo = fun(a: Int): String {
    if (a == 1) return "4"
    when (a) {
        5 -> return "2"
        3 -> return <!NULL_FOR_NONNULL_TYPE!>null<!>
        2 -> return <!CONSTANT_EXPECTED_TYPE_MISMATCH!>2<!>
    }
    return ""
}

val bar: (Int) -> String = l@{ a ->
    if (a == 1) return@l "4"
    when (a) {
        5 -> return@l "2"
        3 -> return@l <!NULL_FOR_NONNULL_TYPE!>null<!>
        2 -> return@l <!CONSTANT_EXPECTED_TYPE_MISMATCH!>2<!>
    }
    return@l ""
}

/* GENERATED_FIR_TAGS: anonymousFunction, equalityExpression, functionalType, ifExpression, integerLiteral,
intersectionType, lambdaLiteral, propertyDeclaration, stringLiteral, whenExpression, whenWithSubject */
