import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The `libs` catalog, which the generated `libs.*` accessors cannot reach from here — they exist
 * only inside a build script. Every version a convention plugin adds still comes from the one
 * catalog, so the rule that no version is hardcoded in a build script holds here too.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
