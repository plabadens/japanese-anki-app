import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val unidicRuntimeArchive = providers.environmentVariable("UNIDIC_RUNTIME_ARCHIVE").orNull
    ?: error("Build inside the Nix development shell to provide pinned UniDic data")
val mecabSourceDir = providers.environmentVariable("MECAB_SOURCE_DIR").orNull
    ?: error("Build inside the Nix development shell to provide pinned MeCab source")
val packageUniDic = tasks.register<Copy>("packageUniDicRuntime") {
    from(unidicRuntimeArchive)
    into(layout.buildDirectory.dir("generated/unidicAssets"))
    rename { "unidic-cwj-3.1.1.zip" }
}

android {
    namespace = "dev.pierre.japaneseanki"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "dev.pierre.japaneseanki"
        minSdk = 37
        targetSdk = 37
        versionCode = 2
        versionName = "0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake { arguments += "-DMECAB_SOURCE_DIR=$mecabSourceDir" }
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

tasks.named("preBuild").configure { dependsOn(packageUniDic) }

if (System.getenv("IN_GRADLE_UPDATE_DEPS") == "1") {
    // AGP resolves this only when release lint executes, after the recorder scans configurations.
    configurations.create("releaseLintForLock") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    dependencies.add("releaseLintForLock", "com.android.tools.lint:lint-gradle:31.13.2") // AGP 8.13.2

    afterEvaluate {
        configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            // The dependency recorder resolves configurations directly, bypassing AGP's
            // artifact selection for the test target's self-project dependency.
            configuration.dependencies.removeAll {
                it is ProjectDependency && it.path == project.path
            }
            // No instrumented tests currently need this configuration.
            if ("AndroidTest" in configuration.name) configuration.isCanBeResolved = false
        }
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
