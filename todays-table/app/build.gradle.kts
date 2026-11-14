import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun resolveLocalConfig(key: String, defaultValue: String = ""): String {
    val raw = localProps.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: System.getenv(key)
        ?: defaultValue
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.mealrater.kxmpzq"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "GEMINI_API_KEY", "\"${resolveLocalConfig("GEMINI_API_KEY")}\"")
        buildConfigField(
            "String",
            "DEFAULT_WEB_APP_URL",
            "\"${resolveLocalConfig("DEFAULT_WEB_APP_URL", "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_GOOGLE_SHEET_URL",
            "\"${resolveLocalConfig("DEFAULT_GOOGLE_SHEET_URL", "https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit?gid=0#gid=0")}\""
        )

        vectorDrawables {
            useSupportLibrary = true
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
