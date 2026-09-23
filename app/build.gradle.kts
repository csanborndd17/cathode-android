plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.twelvepts.cathode"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.twelvepts.cathode"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "0.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    val releaseStorePath = System.getenv("CATHODE_KEYSTORE_FILE")
    val releaseStorePassword = System.getenv("CATHODE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("CATHODE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("CATHODE_KEY_PASSWORD")
    signingConfigs {
        create("release") {
            if (!releaseStorePath.isNullOrBlank()) storeFile = file(releaseStorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            if (listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-session:1.9.3")
    implementation("androidx.media3:media3-common:1.9.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
}
