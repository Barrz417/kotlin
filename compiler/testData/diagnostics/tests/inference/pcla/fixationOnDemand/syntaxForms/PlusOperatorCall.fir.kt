// RUN_PIPELINE_TILL: FRONTEND
fun testRegularNavigation() {
    fun <OT> pcla(lambda: (TypeVariableOwner<OT>) -> Unit): OT = null!!

    val resultA = pcla { otvOwner ->
        otvOwner.constrain(ScopeOwner())
        // should fix OTv := ScopeOwner for scope navigation
        otvOwner.instance + ScopeOwner()
        // expected: Interloper </: ScopeOwner
        otvOwner.constrain(<!ARGUMENT_TYPE_MISMATCH("Interloper; ScopeOwner")!>Interloper<!>)
    }
    // expected: ScopeOwner
    <!DEBUG_INFO_EXPRESSION_TYPE("ScopeOwner")!>resultA<!>
}

fun testSafeNavigation() {
    fun <OT> pcla(lambda: (TypeVariableOwner<OT>?) -> Unit): OT = null!!

    val resultA = pcla { otvOwner ->
        otvOwner?.constrain(ScopeOwner())
        // should fix OTv := ScopeOwner for scope navigation
        otvOwner?.instance <!UNSAFE_OPERATOR_CALL!>+<!> ScopeOwner()
        // expected: Interloper </: ScopeOwner
        otvOwner?.constrain(<!ARGUMENT_TYPE_MISMATCH("Interloper; ScopeOwner")!>Interloper<!>)
    }
    // expected: ScopeOwner
    <!DEBUG_INFO_EXPRESSION_TYPE("ScopeOwner")!>resultA<!>
}


class TypeVariableOwner<T> {
    fun constrain(subtypeValue: T) {}
    val instance: T = null!!
}

interface BaseType

class ScopeOwner: BaseType {
    operator fun plus(other: ScopeOwner): ScopeOwner = this
}

object Interloper: BaseType

/* GENERATED_FIR_TAGS: additiveExpression, checkNotNullCall, classDeclaration, functionDeclaration, functionalType,
interfaceDeclaration, lambdaLiteral, localFunction, localProperty, nullableType, objectDeclaration, operator,
propertyDeclaration, safeCall, thisExpression, typeParameter */
