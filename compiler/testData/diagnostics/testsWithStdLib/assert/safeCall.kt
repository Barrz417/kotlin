// RUN_PIPELINE_TILL: BACKEND
// IGNORE_DATA_FLOW_IN_ASSERT
// SKIP_TXT
// WITH_STDLIB

fun test1(s: String?) {
    assert(s!!.isEmpty())
    s<!UNNECESSARY_SAFE_CALL!>?.<!>length
}

fun test2(s: String?) {
    assert(s!!.isEmpty())
    s<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>.length
}

fun test3(s: String?) {
    assert(s!!.isEmpty())
    <!DEBUG_INFO_SMARTCAST!>s<!>.length
}

fun test4() {
    val s: String? = null;
    assert(s!!.isEmpty())
    s<!UNNECESSARY_SAFE_CALL!>?.<!>length
}

fun test5() {
    val s: String? = null;
    assert(s!!.isEmpty())
    s<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>.length
}

fun test6() {
    val s: String? = null;
    assert(s!!.isEmpty())
    <!DEBUG_INFO_SMARTCAST!>s<!>.length
}

/* GENERATED_FIR_TAGS: checkNotNullCall, functionDeclaration, localProperty, nullableType, propertyDeclaration, safeCall,
smartcast */
