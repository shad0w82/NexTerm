// Vendored from termux-app v0.118.3 (GPLv3). See NOTICE.md in this directory.
plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.termux.emulator"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation(libs.junit)
}
