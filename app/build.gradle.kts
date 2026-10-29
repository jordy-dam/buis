import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "nl.jordy.iptv"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.jordy.iptv"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Instellingen → Versie shows which commit and day this build is, so it's clear what's on the tv.
        val gitSha = runCatching {
            providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()
        }.getOrDefault("onbekend")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
        // Google TV devices are ARM; many (e.g. Chromecast with Google TV) run a 32-bit userspace.
        // x86_64 is only for the emulator on Intel hosts, so it is left out.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            // The tv runs this build: R8 plus a non-debuggable app is what makes Compose smooth
            // on its 32-bit CPU (the debug build had 20% janky frames there).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded only: sign release with the debug key so `installRelease` works without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric pokes at FileDescriptor internals that JDK 17+ no longer exports by default.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
    }

    lint {
        abortOnError = true
        // TV-only app: no touchscreen/leanback banner warnings for phone form factors.
        disable += "MissingTvBanner"
    }

    sourceSets["test"].resources.directories.add(rootProject.file("mockserver/src/main/resources").path)
    // Exported Room schemas, so MigrationTestHelper can build old versions in unit tests. Robolectric
    // only sees the app's own assets, so they go into debug (a few KB; never in release).
    sourceSets["debug"].assets.directories.add("$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg.decoder)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
}
