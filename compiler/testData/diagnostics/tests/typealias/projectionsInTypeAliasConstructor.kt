// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class In<in T>(val x: Any)

typealias InAlias<T> = In<T>

val test1 = In<<!PROJECTION_ON_NON_CLASS_TYPE_ARGUMENT!>out<!> String>("")
val test2 = InAlias<<!PROJECTION_ON_NON_CLASS_TYPE_ARGUMENT!>out<!> String>("")

/* GENERATED_FIR_TAGS: classDeclaration, in, nullableType, primaryConstructor, propertyDeclaration, stringLiteral,
typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
