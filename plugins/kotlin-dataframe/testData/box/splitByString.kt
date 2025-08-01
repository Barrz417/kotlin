import org.jetbrains.kotlinx.dataframe.*
import org.jetbrains.kotlinx.dataframe.annotations.*
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.*

fun box(): String {
    val df = dataFrameOf("a" to listOf("a_b_c")).split { a }.by("_", ".", trim = true, ignoreCase = true, limit = 0).into("a", "b", "c")
    val col: DataColumn<String?> = df.a
    df.assert()
    return "OK"
}
