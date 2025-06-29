// RUN_PIPELINE_TILL: BACKEND
/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-152
 * PRIMARY LINKS: expressions, when-expression -> paragraph 5 -> sentence 1
 * expressions, when-expression, exhaustive-when-expressions -> paragraph 2 -> sentence 9
 * control--and-data-flow-analysis, performing-analysis-on-the-control-flow-graph, variable-initialization-analysis -> paragraph 2 -> sentence 1
 * control--and-data-flow-analysis, performing-analysis-on-the-control-flow-graph, variable-initialization-analysis -> paragraph 2 -> sentence 3
 * control--and-data-flow-analysis, performing-analysis-on-the-control-flow-graph, variable-initialization-analysis -> paragraph 3 -> sentence 2
 */

// Base for KT-6227
enum class X { A, B, C, D }

fun foo(arg: X): String {
    val res: String
    <!DEBUG_INFO_IMPLICIT_EXHAUSTIVE!>when (arg) {
        X.A -> res = "A"
        X.B -> res = "B"
        X.C -> res = "C"
        X.D -> res = "D"
    }<!>
    return res
}

/* GENERATED_FIR_TAGS: assignment, enumDeclaration, enumEntry, equalityExpression, functionDeclaration, localProperty,
propertyDeclaration, smartcast, stringLiteral, whenExpression, whenWithSubject */
