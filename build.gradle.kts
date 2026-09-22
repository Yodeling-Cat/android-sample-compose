// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin bundles compiler 2.2.10; putting a newer Kotlin Gradle plugin on the
// buildscript classpath is the documented way to lift the whole toolchain (compiler + Compose/
// serialization/Mappie plugins, all version-locked to it) to 2.4.10.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint)
}
