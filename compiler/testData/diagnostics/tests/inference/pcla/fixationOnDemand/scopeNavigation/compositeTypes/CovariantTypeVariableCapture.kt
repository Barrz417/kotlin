// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-71843

fun test() {
    val resultA = pcla { otvOwner ->
        otvOwner.constrain(ScopeOwner())
        // expected: CapturedType(out ScopeOwner)
        <!DEBUG_INFO_EXPRESSION_TYPE("TypeVariable(OT)")!>otvOwner.provide()<!>
        // should fix OTv := ScopeOwner for scope navigation
        otvOwner.provide().<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, DEBUG_INFO_UNRESOLVED_WITH_TARGET, UNRESOLVED_REFERENCE!>function<!>()
        // expected: Interloper </: ScopeOwner
        otvOwner.constrain(Interloper)
    }
    // expected: ScopeOwner
    <!DEBUG_INFO_EXPRESSION_TYPE("BaseType")!>resultA<!>
}


class TypeVariableOwner<T, CT> {
    fun constrain(subtypeValue: T) {}
    fun provide(): CT = null!!
}

fun <OT> pcla(lambda: (TypeVariableOwner<OT, out OT>) -> Unit): OT = null!!

interface BaseType

class ScopeOwner: BaseType {
    fun function() {}
}

object Interloper: BaseType

/* GENERATED_FIR_TAGS: capturedType, checkNotNullCall, classDeclaration, functionDeclaration, functionalType,
interfaceDeclaration, lambdaLiteral, localProperty, nullableType, objectDeclaration, outProjection, propertyDeclaration,
typeParameter */
