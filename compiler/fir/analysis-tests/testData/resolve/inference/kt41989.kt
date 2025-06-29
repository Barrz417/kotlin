// RUN_PIPELINE_TILL: BACKEND
// ISSUE: KT-41989

interface A

interface B {
    fun A.withBottomBorder(): A = this
}

interface C : B {
    val lineCellStyle: (A.() -> Unit)?
        get() = if (cond()) {
            {
                withBottomBorder()
            }
        } else null
}

fun cond(): Boolean = true

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration, functionalType, getter, ifExpression,
interfaceDeclaration, lambdaLiteral, nullableType, propertyDeclaration, thisExpression, typeWithExtension */
