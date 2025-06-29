// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// SKIP_JAVAC
// LANGUAGE: +InlineClasses
// ALLOW_KOTLIN_PACKAGE

package kotlin.jvm

annotation class JvmInline

@JvmInline
value class Foo(val x: Int)

<!VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION, WRONG_MODIFIER_TARGET!>value<!> interface InlineInterface
<!VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION, WRONG_MODIFIER_TARGET!>value<!> annotation class InlineAnn
<!VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION, WRONG_MODIFIER_TARGET!>value<!> object InlineObject
<!VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION, WRONG_MODIFIER_TARGET!>value<!> enum class InlineEnum

/* GENERATED_FIR_TAGS: annotationDeclaration, classDeclaration, enumDeclaration, interfaceDeclaration, objectDeclaration,
primaryConstructor, propertyDeclaration, value */
