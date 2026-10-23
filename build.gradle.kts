plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs the complete offline JVM unit, Compose UI, and golden verification lane."
    dependsOn(":app:verifyRoborazziDebug")
}
