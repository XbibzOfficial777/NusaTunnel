plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Production signing is injected by the protected Android Release workflow only.
// Local builds and regular CI remain buildable with the debug key; release CI fails
// closed before building unless all secret-backed signing values are supplied.
val nusaSigningStoreFile = System.getenv("NUSATUNNEL_SIGNING_STORE_FILE")
val nusaSigningStorePassword = System.getenv("NUSATUNNEL_SIGNING_STORE_PASSWORD")
val nusaSigningKeyAlias = System.getenv("NUSATUNNEL_SIGNING_KEY_ALIAS")
val nusaSigningKeyPassword = System.getenv("NUSATUNNEL_SIGNING_KEY_PASSWORD")
val hasNusaReleaseSigning = listOf(
    nusaSigningStoreFile,
    nusaSigningStorePassword,
    nusaSigningKeyAlias,
    nusaSigningKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.nusa.tunnel"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.nusa.tunnel"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "2.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            signingConfig = if (hasNusaReleaseSigning) {
                signingConfigs.findByName("nusa") ?: signingConfigs.create("nusa") {
                    storeFile = file(nusaSigningStoreFile!!)
                    storePassword = nusaSigningStorePassword
                    keyAlias = nusaSigningKeyAlias
                    keyPassword = nusaSigningKeyPassword
                }
            } else {
                // Deliberately not a release identity. Android CI can verify assembly
                // without possessing the protected production keystore.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

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
    implementation(libs.androidx.core.splashscreen)
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

    // JVM tests cover pure logic; device UI/persistence instrumentation runs in KVM-backed GitHub CI.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
