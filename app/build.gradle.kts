plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mappie)
    alias(libs.plugins.ktlint)
}

/**
 * A dev-machine setting, read from an environment variable first, then a Gradle property
 * (`~/.gradle/gradle.properties` or `-P`), then a default. Both sources are lazy providers, so
 * reading one here stays compatible with the configuration cache.
 */
fun devSetting(
    environmentVariable: String,
    gradleProperty: String,
    default: String,
): String =
    providers
        .environmentVariable(environmentVariable)
        .orElse(providers.gradleProperty(gradleProperty))
        .getOrElse(default)

// Where the `local` flavor looks for the Rails server. The default pairs with
// `adb reverse tcp:3000 tcp:3000`, which tunnels the device's own port 3000 to the dev machine over
// adb — the one route that needs no firewall rule and works on the emulator and over USB alike.
// A device reaching the server over the network instead needs the dev machine's LAN address here.
val localServerHost = devSetting("MOSAIC_LOCAL_HOST", "mosaic.localHost", "localhost")
val localServerPort = devSetting("MOSAIC_LOCAL_PORT", "mosaic.localPort", "3000")

android {
    namespace = "uno.lux.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uno.lux.sample"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /*
     * Which server a build talks to is the `server` dimension, not a setting in the app's UI. Both
     * flavors keep the one `applicationId`, so switching is the Build Variants dropdown — or a
     * `Remote`/`Local` task name — and never a reinstall under a different package.
     */
    flavorDimensions += "server"

    productFlavors {
        create("remote") {
            dimension = "server"
            isDefault = true
            buildConfigField("String", "BASE_URL", "\"https://mosaic.tree-among-shrubs.com\"")
        }

        create("local") {
            dimension = "server"
            buildConfigField("String", "BASE_URL", "\"http://$localServerHost:$localServerPort\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // For AppCompatDelegate.setApplicationLocales — the per-app language backport below Android 13.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Declared rather than inherited transitively, so the whole coroutines group resolves at the
    // catalog's version. Left implicit, core lagged behind kotlinx-coroutines-test and every
    // instrumented test using runTest died with NoSuchMethodError.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.timber)
    implementation(libs.mappie.api)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
    // Asserts the package rules in AGENTS.md against the real import graph (ArchitectureTest).
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    // Drives the real Retrofit stack over loopback, so the multipart wire format the backend
    // parses is asserted rather than assumed (see MosaicApiMultipartTest).
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Instrumented ViewModel tests drive viewModelScope the same way the JVM ones do.
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
