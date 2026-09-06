package uno.lux.mosaic.settings.data

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.first
import uno.lux.mosaic.settings.data.domain.AppLanguage

/**
 * [AppLocaleRepository] on the per-app language APIs. `AppCompatDelegate` forwards to the
 * framework's `LocaleManager` on Android 13+ and, below that, keeps the choice itself (enabled by
 * the `autoStoreLocales` flag on the `AppLocalesMetadataHolderService` declared in the manifest).
 * Either way the platform re-applies it before the Activity exists, which is what keeps a cold
 * start from drawing a frame in the wrong language.
 *
 * That platform copy is a cache, not the app's answer: [SettingsRepository] holds the choice, and
 * [applyLanguage] pushes it back down. Applying recreates the Activity, which is what re-reads the
 * resources — hence the guard against re-applying what is already in effect.
 *
 * Both `AppCompatDelegate` locale calls need the delegate to exist, so [applyLanguage] and
 * [resolveInitialLanguage] must not run before an `AppCompatActivity` has attached.
 */
class AppCompatLocaleRepository(
    private val settingsRepository: SettingsRepository,
) : AppLocaleRepository {

    override fun applyLanguage(language: AppLanguage) {
        if (platformLanguage() == language) return

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag),
        )
    }

    override suspend fun resolveInitialLanguage() {
        if (settingsRepository.settings.first().language != null) return

        // A launch that predates the stored setting still has its choice in the platform copy;
        // carrying it over is what keeps an upgrade from silently re-resolving the language.
        val language = platformLanguage()
            ?: AppLanguage.fromLanguageTags(systemLanguageTags())
            ?: AppLanguage.Default

        settingsRepository.setLanguage(language)
    }

    /** The language the platform currently has in effect, or `null` while its locale list is empty. */
    private fun platformLanguage(): AppLanguage? =
        AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /**
     * The device's preferred languages, most-preferred first. Read off the *system* resources
     * rather than the app's, which the per-app locale has already overridden by this point.
     */
    private fun systemLanguageTags(): String =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration).toLanguageTags()
}
