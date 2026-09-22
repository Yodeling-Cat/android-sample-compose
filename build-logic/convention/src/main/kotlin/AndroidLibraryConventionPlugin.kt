import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * What every library module in this build shares: the Android library plugin, the one compileSdk
 * and minSdk pair, Java 11, and ktlint.
 *
 * The SDK levels are stated here rather than in each module because they are a property of the
 * build, not of a module — a library compiled against a different SDK than the app that ships it
 * is a bug waiting for a release. A module's own build file is then just its namespace and its
 * dependencies.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37)
            }

            defaultConfig {
                minSdk = 26
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }
}
