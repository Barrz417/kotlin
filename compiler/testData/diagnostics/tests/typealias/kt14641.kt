// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER -TOPLEVEL_TYPEALIASES_ONLY -UNSUPPORTED_FEATURE

class A {
    public inner class B { }
    public <!WRONG_MODIFIER_TARGET!>inner<!> typealias BAlias = B
}

fun f() {
    val a = A()
    a.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE, NO_COMPANION_OBJECT!>BAlias<!>
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, inner, localProperty, propertyDeclaration,
typeAliasDeclaration */
