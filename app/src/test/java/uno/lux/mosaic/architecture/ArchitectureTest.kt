package uno.lux.mosaic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * The package convention from AGENTS.md, asserted against the real source tree.
 *
 * Kotlin has no package-private and `internal` is module-scoped, so a single-module project gets
 * no compiler enforcement of its own layering — a package layout is a convention until something
 * checks it. These are that something.
 *
 * **Every rule here is derived from the path, never from a list of names.** The concerns are
 * discovered by reading the top-level packages that exist, and each rule then keys off the shape
 * the convention gives them:
 *
 * ```
 * <concern>/data/            the repository and its DataSource interface
 * <concern>/data/domain/     the models — pure Kotlin, no platform and no wire
 * <concern>/data/network/    the service, DTOs, mappers — the only place HTTP appears
 * <concern>/ui/              composables and ViewModels
 * app/                       the machine; wires the concerns, so it may know every one of them
 * designsystem/              what the app is drawn with     (:core:design-system)
 * common/                    what concerns share, utilities included  (:core:common)
 * ```
 *
 * The last two are Gradle modules now, so *their* dependencies are a compile error rather than a
 * rule here. What these rules still add is the other direction: nothing stops a concern from being
 * dragged into one of them, and `internal` cannot express "knows no concern".
 *
 * That is what `every concern package follows the convention` protects, and it is why it matters
 * most: the other rules select files by matching those suffixes, so a file in some invented
 * package would be checked by *none* of them. Enforcing the shape first is what stops a rename
 * from quietly emptying a rule instead of failing it — the previous version of this file hardcoded
 * `uno.lux.sample.app.common..`, and when `common` moved to the top level the rule went on passing
 * while matching nothing at all.
 */
class ArchitectureTest {

    private companion object {
        const val ROOT = "uno.lux.mosaic"

        /** The machine. It wires the concerns together, so it is allowed to import them. */
        const val APP = "app"

        /** What the concerns share. Depends on none of them — see [`common knows no concern`]. */
        const val COMMON = "common"

        /** What the app is drawn with: the palette, the type scale, the branded controls. */
        const val DESIGN_SYSTEM = "designsystem"

        /** The top-level packages that are not a concern, and so have no layer convention. */
        val NON_CONCERNS = setOf(APP, COMMON, DESIGN_SYSTEM)

        /** The shapes a concern's packages may take, as suffixes on `<root>.<concern>`. */
        val LAYERS = listOf(".data", ".data.domain", ".data.network", ".ui")

        val KoFileDeclaration.pkg: String get() = packagee?.name.orEmpty()

        /** The top-level package a file lives in: `uno.lux.mosaic.post.data` -> `post`. */
        val KoFileDeclaration.root: String get() = pkg.removePrefix("$ROOT.").substringBefore('.')

        /** Whether the file is in the concern's data layer: `post.data`, `post.data.network`, … */
        val KoFileDeclaration.isDataLayer: Boolean
            get() = pkg.removePrefix("$ROOT.").substringAfter('.', missingDelimiterValue = "").startsWith("data")

        /**
         * Every production file the rules are about, parsed **once** for the whole class.
         *
         * Konsist scans the whole tree, `build-logic` included, and re-parses it on every call —
         * so a `PRODUCTION` function would have cost ten scans of three modules per run. The
         * filter keeps the convention plugins out without naming them: they declare no package at
         * all, which is what made each one read as a concern named "". A file that *has* a package
         * stays in scope even if it is a foreign one, so a misfiled package fails a rule rather
         * than quietly leaving it.
         */
        val PRODUCTION: List<KoFileDeclaration> =
            Konsist.scopeFromProduction().files.filter { it.packagee != null }

        /** Every concern the tree actually has — discovered, never listed. */
        val CONCERNS: Set<String> = PRODUCTION.map { it.root }.toSet() - NON_CONCERNS
    }

    /** Whether the import names something of ours rather than a library. */
    private fun ours(importName: String): Boolean = importName.startsWith("$ROOT.")

    @Test
    fun `every concern package follows the convention`() {
        val allowed = CONCERNS
            .flatMap { concern -> LAYERS.map { "$ROOT.$concern$it" } }
            .toSet()

        PRODUCTION
            .filter { it.root !in NON_CONCERNS }
            .assertTrue(additionalMessage = CONVENTION_MESSAGE) { it.pkg in allowed }
    }

    @Test
    fun `the wire stays in data-network`() {
        PRODUCTION.assertTrue(additionalMessage = WIRE_MESSAGE) { file ->
            file.pkg.endsWith(".data.network") ||
                file.pkg == "$ROOT.$APP.di" ||
                file.imports.none { it.name.startsWith("retrofit2.") || it.name.startsWith("okhttp3.") }
        }
    }

    @Test
    fun `the domain layer is pure`() {
        PRODUCTION
            .filter { it.pkg.endsWith(".data.domain") }
            .assertTrue(additionalMessage = DOMAIN_MESSAGE) { file ->
                file.imports.none { import ->
                    import.name.startsWith("android.") ||
                        import.name.startsWith("androidx.") ||
                        import.name.startsWith("retrofit2.") ||
                        import.name.startsWith("okhttp3.") ||
                        (ours(import.name) && import.name.contains(".data.network.")) ||
                        (ours(import.name) && import.name.contains(".ui."))
                }
            }
    }

    @Test
    fun `data never depends on ui`() {
        PRODUCTION
            .filter { it.isDataLayer }
            .assertTrue(additionalMessage = LAYERING_MESSAGE) { file ->
                file.imports.none { ours(it.name) && it.name.contains(".ui.") }
            }
    }

    @Test
    fun `common knows no concern`() {
        assertKnowsNoConcern(COMMON, COMMON_MESSAGE)
    }

    @Test
    fun `the design system knows no concern`() {
        assertKnowsNoConcern(DESIGN_SYSTEM, DESIGN_SYSTEM_MESSAGE)
    }

    /**
     * The shared half of the two rules above: a top-level package that every concern may depend on
     * has to depend on none of them. They differ only in which package and which explanation, so
     * the next shared module is a third one-line test rather than a third copy of this body.
     */
    private fun assertKnowsNoConcern(root: String, message: String) {
        PRODUCTION
            .filter { it.root == root }
            .assertTrue(additionalMessage = message) { file ->
                file.imports.none { import ->
                    CONCERNS.any { import.name.startsWith("$ROOT.$it.") }
                }
            }
    }

    @Test
    fun `a screen's ViewModel takes intent only through onEvent`() {
        PRODUCTION
            .filter { it.root !in NON_CONCERNS && it.pkg.endsWith(".ui") }
            .flatMap { it.classes(includeNested = false) }
            .filter { it.name.endsWith("ViewModel") }
            .assertTrue(additionalMessage = VIEW_MODEL_MESSAGE) { viewModel ->
                // Overrides are judged by the supertype check instead: an `override` with no
                // modifier written inherits its visibility, so `onCleared` reads as public here.
                viewModel.parents().all { it.name == "ViewModel" } &&
                    viewModel
                        .functions(includeNested = false, includeLocal = false)
                        .filter { it.hasPublicOrDefaultModifier && !it.hasOverrideModifier }
                        .all { it.name == "onEvent" }
            }
    }

    @Test
    fun `a repository behind no interface stays plain-JVM`() {
        PRODUCTION.assertTrue(additionalMessage = REPOSITORY_MESSAGE) { file ->
            file.classes().none { it.name.endsWith("Repository") && it.parents().isEmpty() } ||
                file.imports.none {
                    it.name.startsWith("android.") || it.name.startsWith("androidx.")
                }
        }
    }
}

private const val CONVENTION_MESSAGE =
    "A concern grew a package the convention doesn't name. Every file under a concern belongs to " +
        "one of `data`, `data/domain`, `data/network` or `ui` — including files that would " +
        "otherwise sit loose at the concern's root. This is the rule the others are built on: they " +
        "select files by matching those suffixes, so a file somewhere else is checked by none of " +
        "them. Either file it under a layer, or widen LAYERS here and say why."

private const val WIRE_MESSAGE =
    "Retrofit or OkHttp appeared outside a `data/network` package. Wire types and HTTP calls belong " +
        "behind a DataSource interface, so a repository can be tested with a fake and swapped to a " +
        "local source without touching its callers. `app/di` is the exception that builds the one " +
        "Retrofit instance."

private const val DOMAIN_MESSAGE =
    "A domain model reached for the platform, the wire, or the screen. `data/domain` is the layer " +
        "everything else is allowed to depend on, which it can only stay if it depends on nothing: " +
        "the mapper in `data/network` converts a DTO into a model, never the reverse."

private const val LAYERING_MESSAGE =
    "A file under `data` imported a `ui` package. Data doesn't know who renders it — that direction " +
        "is what lets a ViewModel be deleted or rewritten without the repository noticing, and it " +
        "keeps the data layer loadable from a plain-JVM test."

private const val COMMON_MESSAGE =
    "`common` imported a concern. It is what every concern shares, so it may know none of them — " +
        "either the helper belongs in that concern, or its parameter should be the plain type it " +
        "really needs (MosaicGradients.avatarBrush took a UserId when all it did was hash a String). " +
        "Note a KDoc [Link] needs an import too: write cross-concern doc references fully qualified."

private const val DESIGN_SYSTEM_MESSAGE =
    "The design system imported a concern. It is what everything is drawn with, and it stays " +
        "reusable only while it knows nothing — which is also why it is the one module every " +
        "other may depend on while it depends on none. A branded control that grew a `Post` " +
        "parameter is post UI: file it in `post/ui`, or take the plain type it really needs."

private const val VIEW_MODEL_MESSAGE =
    "A screen's ViewModel has a public function other than `onEvent`, or implements something " +
        "besides ViewModel. Every intent is a case of the screen's sealed `<Screen>UiEvent`, and " +
        "`onEvent` is the one way in, so the stateless screen takes a single `onEvent` and a test " +
        "can record exactly what it sent. Add a case to the event type and handle it in " +
        "`onEvent`'s `when`, keeping the handler private — not a method on an actions interface. " +
        "`app/ui`'s MainViewModel is outside this rule, since it backs the activity, not a screen."

private const val REPOSITORY_MESSAGE =
    "A repository with no interface above it imported the Android framework, which makes it " +
        "unreachable from a JVM unit test and gives its callers nothing to swap. A repository that " +
        "genuinely needs the platform gets an interface and an in-memory double the way " +
        "SettingsRepository and AppLocaleRepository do — that is what the supertype check is asking " +
        "for, and why those two are not a hardcoded exception here."
