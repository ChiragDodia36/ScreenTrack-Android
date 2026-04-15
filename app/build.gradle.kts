import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Load keystore properties if available
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.chiragdodia.screentrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chiragdodia.screentrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        val backendUrl = keystoreProperties.getProperty("BACKEND_URL")
            ?: System.getenv("BACKEND_URL")
            ?: ""
        val backendApiKey = keystoreProperties.getProperty("BACKEND_API_KEY")
            ?: System.getenv("BACKEND_API_KEY")
            ?: ""
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "BACKEND_API_KEY", "\"$backendApiKey\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = keystoreProperties.getProperty("SCREENTRACK_KEYSTORE_PATH")
                ?: System.getenv("SCREENTRACK_KEYSTORE_PATH")
            val ksPassword = keystoreProperties.getProperty("SCREENTRACK_KEYSTORE_PASSWORD")
                ?: System.getenv("SCREENTRACK_KEYSTORE_PASSWORD")
            val ksKeyAlias = keystoreProperties.getProperty("SCREENTRACK_KEY_ALIAS")
                ?: System.getenv("SCREENTRACK_KEY_ALIAS")
            val ksKeyPassword = keystoreProperties.getProperty("SCREENTRACK_KEY_PASSWORD")
                ?: System.getenv("SCREENTRACK_KEY_PASSWORD")

            if (ksPath != null) {
                storeFile = rootProject.file(ksPath)
                storePassword = ksPassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
            }
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}
