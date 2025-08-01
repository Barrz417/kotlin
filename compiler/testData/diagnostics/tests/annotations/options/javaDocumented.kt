// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: DocumentedAnnotations.java

import java.lang.annotation.*;

public class DocumentedAnnotations {

    @Documented public @interface DocAnn {};

    public @interface NotDocAnn {};

    @Documented @Retention(RetentionPolicy.RUNTIME) public @interface RunDocAnn {};
}

// FILE: DocumentedAnnotations.kt

@DocumentedAnnotations.DocAnn class My

@DocumentedAnnotations.NotDocAnn class Your

@DocumentedAnnotations.RunDocAnn class His

/* GENERATED_FIR_TAGS: classDeclaration, javaType */
