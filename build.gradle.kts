plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs the offline JVM unit and Compose UI assertions without image baselines."
    dependsOn(":app:testDebugUnitTest")
}
