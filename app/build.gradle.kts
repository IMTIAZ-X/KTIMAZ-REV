plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.imtiaz.ktimazstudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.imtiaz.ktimazstudio"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "app/Ktimazstudio.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")

        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
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
            signingConfig = signingConfigs.getByName("release")
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

    externalNativeBuild {
        cmake {
            path(file("src/main/cpp/CMakeLists.txt"))
            version = "3.22.1"
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15" // Make sure it aligns with your Compose version
    }
}

dependencies {
    // Core Libraries
    implementation(libs.androidx.core.ktx) // androidx-core-ktx
    implementation(libs.androidx.lifecycle.runtime.ktx) // androidx-lifecycle-runtime-ktx
    implementation(libs.androidx.activity.compose) // androidx-activity-compose

    // Compose Libraries
    implementation(libs.androidx.ui) // androidx-compose.ui
    implementation(libs.androidx.ui.graphics) // androidx-compose.ui-graphics
    implementation(libs.androidx.ui.tooling.preview) // androidx-compose.ui-tooling-preview
    implementation(libs.androidx.material3) // androidx-compose.material3

    // Test Dependencies
    testImplementation(libs.junit) // junit
    androidTestImplementation(libs.androidx.junit) // androidx-junit
    androidTestImplementation(libs.androidx.espresso.core) // androidx-espresso-core
    androidTestImplementation(libs.androidx.ui.test.junit4) // androidx-ui-test-junit4
    debugImplementation(libs.androidx.ui.tooling) // androidx-ui-tooling
    debugImplementation(libs.androidx.ui.test.manifest) // androidx-ui-test-manifest
}

repositories {
    google()
    mavenCentral()
    // Optional: Add jcenter() if it's necessary for older dependencies
    // jcenter()
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

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
