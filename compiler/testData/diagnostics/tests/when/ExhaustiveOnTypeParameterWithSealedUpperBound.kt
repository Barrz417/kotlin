// FIR_IDENTICAL
sealed class Bird

class Penguin : Bird()
class Ostrich : Bird()
class Kiwi : Bird()

fun <T : Bird> useInstanceInSealedHeirarchy(value: T) {
    val v = <!NO_ELSE_IN_WHEN!>when<!> (value) {
        is Penguin -> "Snow sledding on your belly sounds fun"
        is Ostrich -> "ostentatious and rich"
        is Kiwi -> "kiwiwiwiwi"
    }
}