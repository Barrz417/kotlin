// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: -SuspendFunctionAsSupertype
// SKIP_TXT

import kotlin.reflect.*

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class A<!>: <!SUPERTYPE_IS_KSUSPEND_FUNCTION_TYPE!>KSuspendFunction0<Unit><!> {}

/* GENERATED_FIR_TAGS: classDeclaration */
