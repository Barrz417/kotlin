package test

class Test {
    @ConsistentCopyVisibility
    internal data class ConstructorToBeRemoved internal constructor(internal val fieldToBeRemoved: Any)
}
