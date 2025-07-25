plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.imtiaz.ktimazstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imtiaz.ktimazstudio"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Define signing configurations here
    signingConfigs {
        create("release") {
            // Read keystore path from environment variable
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "")
            // Read keystore password from environment variable
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            // Read key alias from environment variable
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            // Read key password from environment variable
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply the 'release' signing config
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use default debug signing config (no explicit signingConfig needed for debug)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        // Migrate jvmTarget to compilerOptions DSL as recommended
        compilerOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path(file("src/main/cpp/CMakeLists.txt"))
            version = "3.22.1"
        }
    }

    composeOptions {
        // This MUST match Compose BOM + Kotlin version
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // Compose BOM to align versions
    implementation(platform(libs.androidx.compose.bom))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

repositories {
    google()
    mavenCentral()
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
