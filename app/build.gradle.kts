import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.dev1.naturequest"
    compileSdk = 37

    defaultConfig {
        applicationId = "mx.dev1.naturequest"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // LiteRT-LM ships arm64-v8a and x86_64; the model needs a real phone, so arm64 only.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Signing material lives outside git (see keystore.properties.example). Without it the release
        // build is simply unsigned, so anyone can still build the project.
        val keystoreFile = rootProject.file("keystore.properties")
        if (keystoreFile.isFile) {
            create("release") {
                val keystore = Properties().apply { keystoreFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Spike only: /samples photos are bundled into debug builds, never into release.
        getByName("debug") {
            assets.directories.add(rootProject.file("samples").path)
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Missing/extra translations and hardcoded text must fail the build (EN default + ES).
        error += setOf("MissingTranslation", "ExtraTranslation", "HardcodedText")
        // Version-bump nags are noise for a time-boxed build; versions live in the catalog.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // Phones only: the on-device model runs on arm64 hardware, so x86_64 (ChromeOS) is not shipped.
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litertlm.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
