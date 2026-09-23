package uno.lux.mosaic.settings.data

import kotlinx.coroutines.flow.first
import uno.lux.mosaic.settings.data.domain.AppLanguage

interface AppLocaleRepository {

    /** A no-op when [language] is already in effect, so it is safe to call on every emission. */
    fun applyLanguage(language: AppLanguage)

    /** A no-op once a language is stored, so it is safe to call on every launch. */
    suspend fun resolveInitialLanguage()
}

class InMemoryAppLocaleRepository(
    private val settingsRepository: SettingsRepository,
    private val systemLanguageTags: String = "",
) : AppLocaleRepository {

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
