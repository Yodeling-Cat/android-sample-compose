package uno.lux.mosaic.settings.data

import kotlinx.coroutines.flow.first
import uno.lux.mosaic.settings.data.domain.AppLanguage

/**
 * Applies the app's language to the platform. The chosen language is stored by
 * [SettingsRepository]; this only projects it onto the per-app locale APIs, which is what makes
 * the resources change.
 */
interface AppLocaleRepository {

    /** Applies [language]. A no-op when it is already in effect, so it is safe to call on every emission. */
    fun applyLanguage(language: AppLanguage)

    /**
     * Pins a language on first launch, when nothing is stored yet: the best of the device's
     * preferred languages that we ship, or [AppLanguage.Default] when we ship none of them. A no-op
     * once a language is stored, so it is safe to call on every launch — and it must be, because
     * the stored language is what keeps the app steady if the device's language later changes.
     */
    suspend fun resolveInitialLanguage()
}

/**
 * @param systemLanguageTags stands in for the device's preferred languages, as a BCP 47 tag list.
 */
class InMemoryAppLocaleRepository(
    private val settingsRepository: SettingsRepository,
    private val systemLanguageTags: String = "",
) : AppLocaleRepository {

    /** The languages handed to [applyLanguage], in order, for a test to assert what reached the platform. */
    val applied = mutableListOf<AppLanguage>()

    override fun applyLanguage(language: AppLanguage) {
        if (applied.lastOrNull() == language) return

        applied += language
    }

    override suspend fun resolveInitialLanguage() {
        if (settingsRepository.settings.first().language != null) return

        settingsRepository.setLanguage(
            AppLanguage.fromLanguageTags(systemLanguageTags) ?: AppLanguage.Default,
        )
    }
}
