package test

class Test {
    @ConsistentCopyVisibility
    internal data class ClassToBeRemoved internal constructor(val a: Any)

    @ConsistentCopyVisibility
    data class EverythingExceptTheClassAndPropertyToBeRemoved internal constructor(val a: Any)
}
