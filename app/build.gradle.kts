import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val unidicRuntimeArchive = providers.environmentVariable("UNIDIC_RUNTIME_ARCHIVE").orNull
    ?: error("Build inside the Nix development shell to provide pinned UniDic data")
val packageUniDic = tasks.register<Copy>("packageUniDicRuntime") {
    from(unidicRuntimeArchive)
    into(layout.buildDirectory.dir("generated/unidicAssets"))
    rename { "unidic-cwj-3.1.1.zip" }
}

android {
    namespace = "dev.pierre.japaneseanki"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.pierre.japaneseanki"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    androidResources {
        noCompress += listOf("gz", "zip")
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/unidicAssets"))

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

tasks.named("preBuild").configure { dependsOn(packageUniDic) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
