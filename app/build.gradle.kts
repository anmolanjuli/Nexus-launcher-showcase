plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nexus.launcher"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nexus.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Resource shrinking is deliberately NOT enabled alongside this.
            // DockBackgroundSettingsBinder resolves its own R.id values at runtime by name
            // (`resources.getIdentifier(name, "id", packageName)`), which the resource shrinker
            // cannot see. Code shrinking is unaffected by that — getIdentifier reads the runtime
            // resource table, not the R class — so minification is safe on its own. Turning
            // shrinkResources on is a separate change that needs its own keep list.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Local testing only. Signing release with the debug key is what makes this build
            // installable OVER an existing debug install, keeping its database and DataStore —
            // which is the whole point here, since the risk being tested is R8 corrupting data
            // that is already on the device. A different key would force an uninstall first and
            // hide exactly the bug we are looking for.
            // Replace with a real signing config before publishing anywhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        // ExtraTranslation is fatal by default, so a translation whose default-locale string is
        // missing stops every release build. That happens routinely here while a strings split is
        // in flight — the translated file lands before the default one — and blocking the build on
        // a half-finished translation helps nobody. Still reported as a warning: a string
        // translated with no original is a real bug (it throws at runtime in the default locale),
        // so the warnings must be cleared before publishing.
        warning += "ExtraTranslation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation("com.google.code.gson:gson:2.10.1")
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.billing)
    implementation(libs.androidx.fragment.ktx)
    // Backup folder enumeration (BackupCatalog) — was only arriving transitively.
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
