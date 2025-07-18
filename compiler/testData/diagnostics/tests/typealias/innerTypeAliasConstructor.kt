// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER -TOPLEVEL_TYPEALIASES_ONLY -UNSUPPORTED_FEATURE

class Pair<X, Y>(val x: X, val y: Y)

class C<T> {
    <!WRONG_MODIFIER_TARGET!>inner<!> typealias P = Pair<T, T>
    <!WRONG_MODIFIER_TARGET!>inner<!> typealias P1<X> = Pair<X, T>
    <!WRONG_MODIFIER_TARGET!>inner<!> typealias P2<Y> = Pair<T, Y>
}

val c = C<Int>()
val test0 = c.<!UNRESOLVED_REFERENCE!>P<!>(1, 1)
// TODO (KT-73273): fixing of `WRONG_NUMBER_OF_TYPE_ARGUMENTS` requires a special handling of `MapTypeArguments` resolution stage.
// Currently it takes already expanded constructor call (typealias doesn't have constructor itself) with 2 type arguments,
// but it doesn't consider captured type arguments from an outer instance (it's an extra `Int` type argument).
val test1 = c.<!UNRESOLVED_REFERENCE!>P1<!><String>("", 1)
val test2 = c.<!UNRESOLVED_REFERENCE!>P2<!><String>(1, "")
val test3 = c.<!UNRESOLVED_REFERENCE!>P1<!>("", 1)
val test4 = c.<!UNRESOLVED_REFERENCE!>P2<!>(1, "")

/* GENERATED_FIR_TAGS: classDeclaration, integerLiteral, nullableType, primaryConstructor, propertyDeclaration,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
