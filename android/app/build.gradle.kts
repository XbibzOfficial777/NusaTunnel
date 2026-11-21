plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nusa.tunnel"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.nusa.tunnel"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "2.2.4"
        vectorDrawables { useSupportLibrary = true }
    }

    // ==== APK per-arsitektur + universal (v2.2.1) ====
    // Output: arm64-v8a, armeabi-v7a, x86, x86_64 + 1 APK universal
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // diganti signing release di bawah
        }
        create("nusaRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isMinifyEnabled = false      // build pertama tanpa R8 utk keandalan engine gobind
            isShrinkResources = false
            signingConfig = signingConfigs.create("nusa") {
                storeFile = rootProject.file("nusatunnel.keystore")
                storePassword = "nusatunnel2026"
                keyAlias = "nusatunnel"
                keyPassword = "nusatunnel2026"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Keep download APKs compact; Android extracts the compressed native libraries at install.
        jniLibs.useLegacyPackaging = true
    }

    // GeoIP/geosite assets are compressed inside the APK and decompressed by AssetManager
    // when copied into filesDir for Xray. Leaving .dat uncompressed bloats every APK variant.
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Engine Xray-core (gobind) plus optional default TUN packet processor HevSocks5Tunnel.
    // CI downloads both pinned AARs into libs/ before the Android build.
    implementation(files("libs/libxray.aar"))
    implementation(files("libs/hev-socks5-tunnel.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit test JVM (LinkParser / XrayConfigBuilder / SmartImporter)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
