import org.gradle.api.artifacts.ProjectDependency
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

val mecabSourceDir = providers.environmentVariable("MECAB_SOURCE_DIR").orNull
    ?: error("Build inside the Nix development shell to provide pinned MeCab source")
val robolectricDependencyDir = providers.environmentVariable("ROBOLECTRIC_DEPENDENCY_DIR")
    .orElse(file("$rootDir/robolectric-jars/preinstrumented").absolutePath)
    .get()
val testTempDir = providers.environmentVariable("TMPDIR")
    .orElse(layout.projectDirectory.dir("build/tmp").asFile.absolutePath)
    .get()
val robolectricTestLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}

// Release signing material is read from the environment and never from the repository. Every
// value is optional: when they are absent `assembleRelease` keeps producing the unsigned
// variant that `nix build .#apk`, local builds and the debug CI workflow already expect. When
// they are all present, the release APK is signed with the stable release identity, which is
// what lets the phone update an installed build in place.
val releaseKeystoreFile: java.io.File? = providers.environmentVariable("JAPANESE_ANKI_RELEASE_KEYSTORE")
    .orNull
    ?.let { file(it) }
val releaseKeystorePassword: String? =
    providers.environmentVariable("JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.environmentVariable("JAPANESE_ANKI_RELEASE_KEY_ALIAS").orNull
// A PKCS12 keystore holds one password; reuse the store password unless one is given separately.
val releaseKeyPassword: String? =
    providers.environmentVariable("JAPANESE_ANKI_RELEASE_KEY_PASSWORD").orNull
        ?: releaseKeystorePassword
val releaseSigningConfigured: Boolean =
    (releaseKeystoreFile?.isFile == true) &&
    !releaseKeystorePassword.isNullOrEmpty() &&
    !releaseKeyAlias.isNullOrEmpty() &&
    !releaseKeyPassword.isNullOrEmpty()

if (releaseKeystoreFile != null && !releaseSigningConfigured) {
    logger.warn(
        "JAPANESE_ANKI_RELEASE_KEYSTORE is set but the release signing environment is " +
            "incomplete or the file does not exist; assembleRelease will produce an UNSIGNED APK.",
    )
}

android {
    namespace = "dev.pierre.japaneseanki"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.2.12479018"

    // Keep the sideload update identity stable. A path supplied here is the keystore used for
    // debug builds; without it Gradle silently generates a throwaway key per ANDROID_USER_HOME,
    // which makes an installed build impossible to update in place.
    val sharedDebugKeystore = providers.environmentVariable("JAPANESE_ANKI_DEBUG_KEYSTORE").orNull
    if (sharedDebugKeystore != null) {
        signingConfigs.create("sharedDebug") {
            storeFile = file(sharedDebugKeystore)
            storePassword = providers.environmentVariable("JAPANESE_ANKI_DEBUG_KEYSTORE_PASSWORD").getOrElse("android")
            keyAlias = providers.environmentVariable("JAPANESE_ANKI_DEBUG_KEY_ALIAS").getOrElse("androiddebugkey")
            keyPassword = providers.environmentVariable("JAPANESE_ANKI_DEBUG_KEY_PASSWORD").getOrElse("android")
        }
    }

    // Release signing identity, present only when the environment provides it (see above).
    if (releaseSigningConfigured) {
        signingConfigs.create("releaseEnv") {
            storeFile = releaseKeystoreFile
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "dev.pierre.japaneseanki"
        minSdk = 37
        targetSdk = 37
        // 0.3 shipped as versionCode 3; the update must be strictly newer.
        versionCode = 4
        versionName = "0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake { arguments += "-DMECAB_SOURCE_DIR=$mecabSourceDir" }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    androidResources {
        noCompress += "gz"
    }

    buildTypes {
        if (sharedDebugKeystore != null) {
            getByName("debug") { signingConfig = signingConfigs.getByName("sharedDebug") }
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("releaseEnv")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.javaLauncher.set(robolectricTestLauncher)
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", robolectricDependencyDir)
                it.systemProperty("java.io.tmpdir", testTempDir)
                it.systemProperty("org.conscrypt.native.workdir", testTempDir)
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

if (System.getenv("IN_GRADLE_UPDATE_DEPS") == "1") {
    // AGP resolves this only when release lint executes, after the recorder scans configurations.
    configurations.create("releaseLintForLock") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    dependencies.add("releaseLintForLock", "com.android.tools.lint:lint-gradle:32.4.1") // AGP 9.4.1

    afterEvaluate {
        configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            // The dependency recorder resolves configurations directly, bypassing AGP's
            // artifact selection for the test target's self-project dependency.
            configuration.dependencies.removeAll {
                it is ProjectDependency && it.path == project.path
            }
        }
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "2g"
    failFast = true
    reports.junitXml.required.set(true)
}
