plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.imtiaz.ktimazstudio"
    // compileSdk 36 (Android 16) is the latest. Keep this.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.imtiaz.ktimazstudio"
        minSdk = 24
        // targetSdk 36 (Android 16) is the latest. Keep this.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17") // Use C++17 standard
                arguments("-DANDROID_STL=c++_shared") // Use shared STL library
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    androidComponents {
        onVariants { variant ->
            if (variant.buildType == "release") {
                variant.packaging.resources.excludes.addAll(
                    listOf(
                        "kotlin/**",
                        "kotlin-tooling-metadata.json",
                        "assets/dexopt/**",
                        "META-INF/LICENSE",
                        "META-INF/DEPENDENCIES",
                        "META-INF/*.kotlin_module",
                        "**/DebugProbesKt.bin",
                        "okhttp3/internal/publicsuffix/NOTICE",
                        "okhttp3/**",
                        "/META-INF/{AL2.0,LGPL2.1}"
                    )
                )
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path(file('src/main/cpp/CMakeLists.txt')) // This points to your CMakeLists.txt
            version = "3.22.1" // Or your installed CMake version. This is a common and relatively recent version.
        }
    }
    
    // For Compose, you need to explicitly set the composeCompilerExtensionVersion
    composeOptions {
        // The latest stable Compose Compiler is 1.5.15 as of August 7, 2024.
        // However, for the very latest UI/Material3 versions, you often need a newer compiler version, 
        // which might be in beta. I'll use 1.9.0-beta03 as it aligns with the 1.9.x compose UI versions.
        // Always check the compatibility matrix for your Compose BOM.
        // For BOM 2025.07.01 or newer (which aligns with Android 16/Compose 1.9.x), a newer compiler is needed.
        kotlinCompilerExtensionVersion = "1.9.0-beta03" 
    }
}

dependencies {

    // These should ideally be managed by the Compose BOM to ensure compatibility.
    // If you explicitly declare them, ensure they align with your chosen BOM version.
    // However, since you had them explicitly, I'm updating them to their latest stable or beta compatible versions.
    implementation("androidx.activity:activity-compose:1.9.0-beta03") // Latest beta that aligns with 1.9.x Compose UI
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2") // Latest stable

    implementation(libs.androidx.core.ktx) // Updated below in versions
    implementation(libs.androidx.lifecycle.runtime.ktx) // Updated below in versions
    implementation(libs.androidx.activity.compose) // This should be covered by the BOM if possible, or specified directly with the latest.
    implementation(platform("androidx.compose:compose-bom:2025.07.01")) // Latest stable BOM as of July 2025
    implementation(libs.androidx.ui) // Updated below in versions
    implementation(libs.androidx.ui.graphics) // Updated below in versions
    implementation(libs.androidx.ui.tooling.preview) // Updated below in versions
    implementation(libs.androidx.material3) // Updated below in versions
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2") // Latest stable JUnit 4
    androidTestImplementation("androidx.test.ext:junit:1.3.0-rc01") // Latest release candidate
    androidTestImplementation("androidx.espresso:espresso-core:3.6.1") // Latest stable (derived from AndroidX releases)
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.07.01")) // Match the main compose bom
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.0-beta03") // Latest beta that aligns with 1.9.x Compose UI
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0-beta03") // Latest beta that aligns with 1.9.x Compose UI
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0-beta03") // Latest beta that aligns with 1.9.x Compose UI
}