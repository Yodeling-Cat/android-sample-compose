package uno.lux.mosaic.settings.data

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.first
import uno.lux.mosaic.settings.data.domain.AppLanguage

/** [applyLanguage] and [resolveInitialLanguage] need an attached `AppCompatActivity`. */
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

    private fun platformLanguage(): AppLanguage? =
        AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Read off the *system* resources: the app's are already overridden by the per-app locale. */
    private fun systemLanguageTags(): String =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration).toLanguageTags()
}
