// RUN_PIPELINE_TILL: FRONTEND
interface IStr {
    val foo: String
}

class CStr : IStr {
    override val foo: String get() = ""
}

interface IInt {
    val foo: Int
}

class CInt : IInt {
    override val foo: Int get() = 42
}

interface IAny {
    val foo: Any
}

class CAny : IAny {
    override val foo: Any get() = null!!
}

interface IGeneric<T> {
    val foo: T
}

class CGeneric<T> : IGeneric<T> {
    override val foo: T get() = null!!
}

abstract <!PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test1<!> : IStr by CStr(), IInt

abstract <!PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test2<!> : IStr, IInt by CInt()

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test3<!> : IStr by CStr(), IInt by CInt()

abstract class Test4 : IStr by CStr(), IGeneric<String>

abstract class Test5 : IStr by CStr(), IGeneric<Any>

abstract <!PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test6<!> : IStr by CStr(), IGeneric<Int>

abstract class Test7 : IGeneric<String> by CGeneric<String>(), IStr

abstract <!PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test8<!> : IGeneric<String> by CGeneric<String>(), IInt

// Can't test right now due to https://youtrack.jetbrains.com/issue/KT-10258
// abstract class Test9 : IGeneric<String> by CGeneric<String>(), IGeneric<Int>

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, MANY_IMPL_MEMBER_NOT_IMPLEMENTED, PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test10<!> : IInt by CInt(), IStr by CStr(), IAny by CAny()

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test11<!> : IInt, IStr by CStr(), IAny by CAny()

abstract <!PROPERTY_TYPE_MISMATCH_ON_INHERITANCE!>class Test12<!> : IInt, IStr, IAny by CAny()

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, getter, inheritanceDelegation, integerLiteral,
interfaceDeclaration, nullableType, override, propertyDeclaration, stringLiteral, typeParameter */
