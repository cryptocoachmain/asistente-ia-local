plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "com.dnklabs.asistenteialocal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dnklabs.asistenteialocal"
        minSdk = 26
        targetSdk = 34
        versionCode = 1038
        versionName = "1.3.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // NDK para llama.cpp
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-DNDEBUG")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DLLAMA_CUBLAS=OFF",
                    "-DLLAMA_METAL=OFF",
                    "-DLLAMA_OPENMP=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../DNKLabs.jks")
            storePassword = "DNK420dnk"
            keyAlias = "dnklabs"
            keyPassword = "DNK420dnk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = true  // Allow debugging of release builds
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Room (Base de datos)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Seguridad - Encriptación
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // OCR - Google ML Kit (on-device, bundled with APK)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:language-id:17.0.4")
    
    // PDF Processing - PDFBox Android (bundled with APK)
    implementation("com.tom-roush:pdfbox-android:2.0.24.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
