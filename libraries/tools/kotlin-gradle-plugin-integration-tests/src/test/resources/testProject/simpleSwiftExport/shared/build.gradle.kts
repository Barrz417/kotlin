plugins {
    kotlin("multiplatform")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportApi::class)
    swiftexport {
        name = "Shared"
    }
}
