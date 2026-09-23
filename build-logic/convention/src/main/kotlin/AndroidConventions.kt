import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

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

/** The BOM is `api`, so every consuming module resolves the same Compose versions. */
internal fun Project.configureCompose(extension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    extension.buildFeatures.compose = true

    dependencies {
        add("api", platform(libs.findLibrary("androidx-compose-bom").get()))
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
