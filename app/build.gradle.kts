plugins {
    id("com.android.application")
    kotlin("android")
}

// Keep signing credentials outside source control. Without them assembleRelease is unsigned.
val releaseStore = providers.environmentVariable("CAREWINDOW_KEYSTORE")
android {
    namespace = "ai.carewindow"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig {
        applicationId = "ai.carewindow.pleos.starter"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (releaseStore.isPresent) {
            create("release") {
                storeFile = file(releaseStore.get())
                storePassword = providers.environmentVariable("CAREWINDOW_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CAREWINDOW_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CAREWINDOW_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        debug { versionNameSuffix = "-debug-starter" }
        release {
            isMinifyEnabled = false
            if (releaseStore.isPresent) signingConfig = signingConfigs.getByName("release")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation(project(":core"))
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
}
