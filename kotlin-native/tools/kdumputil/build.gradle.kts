plugins {
    kotlin("jvm")
    application
}

group = "org.jetbrains.kdumputil"
version = "1.0.0"

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("MainKt")
}
