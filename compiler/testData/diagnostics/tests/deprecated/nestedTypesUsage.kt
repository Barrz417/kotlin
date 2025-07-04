// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class TopLevel {
    @Deprecated("Nested")
    class Nested {
        companion object {
            fun use() {}

            class CompanionNested2
        }

        class Nested2
    }
}

fun useNested() {
    val d = TopLevel.<!DEPRECATION!>Nested<!>.use()
    TopLevel.<!DEPRECATION!>Nested<!>.Nested2()
    TopLevel.<!DEPRECATION!>Nested<!>.<!UNRESOLVED_REFERENCE!>CompanionNested2<!>()
}

/* GENERATED_FIR_TAGS: classDeclaration, companionObject, functionDeclaration, localProperty, nestedClass,
objectDeclaration, propertyDeclaration, stringLiteral */
