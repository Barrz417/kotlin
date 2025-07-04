// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// LANGUAGE: -ProperForInArrayLoopRangeVariableAssignmentSemantic
// DIAGNOSTICS: -UNUSED_VALUE
// SKIP_TXT

var xs: IntArray = intArrayOf(1, 2, 3)
    get() = field
    set(ys) {
        var sum = 0
        for (x in field) {
            sum = sum * 10 + x
            field = intArrayOf(4, 5, 6)
        }
        if (sum != 123) throw AssertionError("sum=$sum")
        field = ys
    }

/* GENERATED_FIR_TAGS: additiveExpression, assignment, equalityExpression, forLoop, getter, ifExpression, integerLiteral,
localProperty, multiplicativeExpression, propertyDeclaration, setter, stringLiteral */
