import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * The settings every module in this build shares, whether it is the application or a library.
 *
 * The SDK levels live here rather than in each module because they are a property of the build,
 * not of a module: a library compiled against a different SDK than the app that ships it is a bug
 * waiting for a release. `targetSdk` is deliberately *not* here — only the application declares
 * one, and bumping it is the reviewed decision AGENTS.md describes.
 */
internal fun Project.configureAndroid(extension: CommonExtension) {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extension.compileSdk {
        version = release(37)
    }

    extension.defaultConfig.minSdk = 26

    with(extension.compileOptions) {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/**
 * Compose, wherever it is drawn.
 *
 * The BOM is `api` rather than `implementation` so a consuming module inherits the same version
 * constraints; two modules resolving different Compose versions is the failure this prevents.
 */
internal fun Project.configureCompose(extension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    extension.buildFeatures.compose = true

    dependencies {
        add("api", platform(libs.findLibrary("androidx-compose-bom").get()))
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
