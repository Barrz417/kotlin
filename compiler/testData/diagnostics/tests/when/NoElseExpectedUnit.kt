// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-152
 * PRIMARY LINKS: expressions, when-expression -> paragraph 5 -> sentence 1
 * expressions, when-expression -> paragraph 9 -> sentence 2
 * expressions, when-expression, exhaustive-when-expressions -> paragraph 1 -> sentence 1
 */

fun foo(x: Int) {
    val y: Unit = <!NO_ELSE_IN_WHEN!>when<!> (x) {
        2 -> {}
        3 -> {}
    }
    return y
}

/* GENERATED_FIR_TAGS: equalityExpression, functionDeclaration, integerLiteral, localProperty, propertyDeclaration,
whenExpression, whenWithSubject */
