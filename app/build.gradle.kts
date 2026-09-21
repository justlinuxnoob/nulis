// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nulis.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nulis.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    buildFeatures {
        compose = true
        // The version name ends up in a backup file, so a person reading one knows what wrote it.
        buildConfig = true
    }
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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
