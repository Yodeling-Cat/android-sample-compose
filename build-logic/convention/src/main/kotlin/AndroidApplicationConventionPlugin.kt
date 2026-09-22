import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The application module. It shares the SDK levels, Java version and Compose setup with the
 * libraries, which is the whole point: the app and the libraries it ships must compile against
 * one SDK, and stating that in two files is how they drift.
 *
 * Compose comes with it unconditionally, because the app is 100% Compose and a second,
 * Compose-less application is not a thing this build will grow.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            configureCompose(this)
        }
    }
}
