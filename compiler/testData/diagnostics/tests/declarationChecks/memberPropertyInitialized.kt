// ISSUE: KT-68556

class Clazz() {
    <!PROPERTY_WITH_NO_TYPE_NO_INITIALIZER!>var foo<!>

    init {
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>foo<!> = "hello"
    }
}
