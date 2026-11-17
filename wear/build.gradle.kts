plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.huimao.fairway"
    compileSdk = 36

    defaultConfig {
        // 与手机端同包名，Play 靠包名把手表 APK 和手机 APK 关联到同一应用
        applicationId = "com.huimao.fairway"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val bom = libs.androidx.compose.bom
    implementation(platform(bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // Wear Compose Material3 1.6.x（不要 1.7.x：要 compileSdk 37 + AGP 9.1）
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    // 手表联动：Wear OS Data Layer + 高尔夫运动记录
    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)

    debugImplementation(libs.wear.compose.ui.tooling)

    testImplementation(libs.junit)
}
