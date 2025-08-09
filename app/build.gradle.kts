plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.imtiaz.ktimazrev"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.imtiaz.ktimazrev"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // signingConfigs block has been removed as requested.

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The line 'signingConfig = signingConfigs.getByName("release")' has been removed.
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // This will work but will show a deprecation warning.
    kotlinOptions {
        jvmTarget = "17"
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
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
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
