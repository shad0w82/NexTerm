plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support, so no org.jetbrains.kotlin.android plugin here.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.nexterm"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.nexterm"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal test installs: signed with the debug key so the release build can replace the debug build in place and keep its data.
            // A build to publish needs its own keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.usb.serial)
    implementation(project(":terminal-view"))
    testImplementation(libs.junit)
}
