// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class A(<!ABSTRACT_PROPERTY_IN_PRIMARY_CONSTRUCTOR_PARAMETERS!>abstract<!> val i: Int)
class B(<!WRONG_MODIFIER_TARGET!>abstract<!> i: Int)

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration */
