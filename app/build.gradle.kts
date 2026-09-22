import java.io.File
import java.util.Properties

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

// Where the `local` flavor looks for the Rails server. The default pairs with the adb reverse
// tunnel opened by `adbReverseLocalServer` below — the one route that needs no firewall rule and
// works on the emulator and over USB alike. A device reaching the server over the network instead
// needs the dev machine's LAN address here, and then no tunnel is opened.
val localServerHost = devSetting("MOSAIC_LOCAL_HOST", "mosaic.localHost", "localhost")
val localServerPort = devSetting("MOSAIC_LOCAL_PORT", "mosaic.localPort", "3000")

android {
    namespace = "uno.lux.mosaic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uno.lux.mosaic"
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
    implementation(project(":core:common"))
    implementation(project(":core:design-system"))
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
    testImplementation(testFixtures(project(":core:common")))
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

/**
 * Forwards each attached device's own [port] to this machine's, with `adb reverse`.
 *
 * The `local` flavor's default host is loopback *on the device*, which is only the dev machine
 * because of this tunnel. adb is the route rather than an IP because the server runs under WSL,
 * whose mirrored networking relays a Linux listener to Windows processes only — see *Which server
 * a build talks to* in AGENTS.md.
 *
 * **The task never fails the build.** Assembling an APK with no device plugged in is ordinary, and
 * so is having no SDK on a machine that only runs the JVM checks. Every failure is reported as one
 * line and swallowed.
 */
abstract class AdbReverseTask : DefaultTask() {

    @get:Input
    abstract val adbExecutable: Property<String>

    @get:Input
    abstract val port: Property<String>

    init {
        // The tunnel dies with the emulator and with the adb server, so an up-to-date result would
        // mean a tunnel that is silently absent. It is a few milliseconds; just run it every time.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun openTunnels() {
        val adb = adbExecutable.get()
        val tunnelPort = port.get()

        val (listed, devices) = execute(adb, "devices")
        if (listed != 0) {
            logger.lifecycle("adb reverse: skipped, `adb devices` failed. $devices")
            return
        }

        val serials = devices
            .lineSequence()
            .drop(1)
            .map { it.trim().split(Regex("""\s+""")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it.first() }
            .toList()

        if (serials.isEmpty()) {
            logger.lifecycle("adb reverse: no device attached, no tunnel opened.")
            return
        }

        serials.forEach { serial ->
            val (code, output) = execute(adb, "-s", serial, "reverse", "tcp:$tunnelPort", "tcp:$tunnelPort")

            if (code == 0) {
                logger.lifecycle("adb reverse: $serial localhost:$tunnelPort -> this machine")
            } else {
                logger.lifecycle("adb reverse: $serial failed. $output")
            }
        }
    }

    /** Runs [command], returning its exit code and merged output. A missing adb reads as an error. */
    private fun execute(vararg command: String): Pair<Int, String> =
        try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()

            process.waitFor() to output.trim()
        } catch (e: java.io.IOException) {
            -1 to "Could not run ${command.first()}: ${e.message}"
        }
}

/** The SDK's `adb`, falling back to whatever the PATH provides. */
fun findAdb(): String {
    val executable = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"

    val sdkDirectory = providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orNull
        ?.let { Properties().apply { load(it.reader()) }.getProperty("sdk.dir") }

    val roots = listOfNotNull(
        providers.environmentVariable("ANDROID_HOME").orNull,
        providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
        sdkDirectory,
    )

    return roots
        .map { File(it, "platform-tools/$executable") }
        .firstOrNull { it.isFile }
        ?.absolutePath
        ?: executable
}

val adbReverseLocalServer = tasks.register<AdbReverseTask>("adbReverseLocalServer") {
    group = "install"
    description = "Opens the adb reverse tunnel the `local` flavor's default host relies on."

    adbExecutable.set(findAdb())
    port.set(localServerPort)
}

/*
 * Hung off assemble rather than install, because Android Studio's Run button deploys the APK
 * itself and never calls the install task. An override to a LAN address reaches the server over
 * the network, so it wires up nothing.
 */
if (localServerHost in setOf("localhost", "127.0.0.1")) {
    tasks
        .matching { it.name.startsWith("assembleLocal") || it.name.startsWith("installLocal") }
        .configureEach { finalizedBy(adbReverseLocalServer) }
}
