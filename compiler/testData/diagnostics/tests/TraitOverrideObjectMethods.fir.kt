// RUN_PIPELINE_TILL: FRONTEND
// JAVAC_EXPECTED_FILE
// WITH_EXTRA_CHECKERS
interface MyTrait: <!INTERFACE_WITH_SUPERCLASS, PLATFORM_CLASS_MAPPED_TO_KOTLIN!>Object<!> {
    override fun toString(): String
    public override fun finalize()
    <!REDUNDANT_VISIBILITY_MODIFIER!>public<!> <!OVERRIDING_FINAL_MEMBER!>override<!> fun wait()
}

/* GENERATED_FIR_TAGS: functionDeclaration, interfaceDeclaration, override */
