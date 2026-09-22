import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** A library module that draws something: the base convention plus Compose. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("mosaic.android.library")

        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}
