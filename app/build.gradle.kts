// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.nulis.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nulis.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key lives on the owner's own computer and nowhere else: never in this
    // repository, never on a build server that did not ask for it. When the file is there, a
    // release build is signed with it; when it is not, a release build is still made, unsigned,
    // so nobody else's build ever fails for want of somebody else's key.
    // scripts/make-release-key.sh makes one.
    val releaseKeyFile = File(System.getProperty("user.home"), ".nulis-release/keystore.properties")
    val releaseKey = releaseKeyFile.takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = File(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    // Two builds of one app, identical but for one line in About. Google Play does not allow a
    // payment or tip link that is not Play's own billing, so the Play build has none at all -
    // not a hidden one, not a string in the APK. The GitHub build, which is what the releases
    // page and F-Droid-style installs get, carries a "Buy me a coffee" link.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("github") {
            dimension = "store"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release-optimised (R8, not debuggable) but signed with the debug key, so it installs over
        // the debug build on a test phone and keeps its data. This is what goes on the phone.
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Warnings are errors: the point of running lint is to keep the list at zero.
        warningsAsErrors = true
        abortOnError = true
        disable += setOf(
            // "A newer version of X is available" is a decision, not a defect, and upgrading a
            // dependency is something to do deliberately, with a build and a test run after it.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            // The adaptive icon lives in mipmap-anydpi-v26 because aapt2 will not take the
            // unversioned folder; lint calls the qualifier redundant, aapt2 disagrees.
            "ObsoleteSdkInt",
            // Same reasoning as GradleDependency, and it has a sharper edge: lint asks the
            // network what the newest API level is, so this fires the day a new one appears and
            // breaks a build that has not changed. targetSdk is raised deliberately, with the
            // platform installed and the app run against it, not by a machine that noticed.
            "OldTargetApi",
        )
    }

    testOptions {
        unitTests {
            // Robolectric renders the real screens on the JVM for the screenshot tests, and it
            // needs the merged resources and fonts to do it.
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                // The screenshot tours (src/testDebug) draw with the real renderer. Images are only
                // written by `./gradlew recordRoborazziPlayDebug`; a plain run still drives every
                // screen, so a crash in one still fails the build.
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Android 16's framework reaches into FileDescriptor internals, which Robolectric
                // can only intercept on a JDK that lets it in.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }

    // The dependency list AGP would otherwise sign into the APK is an encrypted blob only Google
    // can read. F-Droid rejects APKs that carry it, and an open-source APK has its dependencies in
    // the open anyway. Bundles keep it: Play uses it for its SDK warnings.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    buildFeatures {
        compose = true
        // The version name ends up in a backup file, so a person reading one knows what wrote it.
        buildConfig = true
    }
}

// The screenshot tours launch the whole launcher, and DataStore keeps its state per process: one
// tour finishing onboarding would otherwise start the next one past it. A fresh JVM per test
// class keeps every tour on a phone of its own.
tasks.withType<Test>().configureEach {
    if (name.endsWith("DebugUnitTest")) forkEvery = 1
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
