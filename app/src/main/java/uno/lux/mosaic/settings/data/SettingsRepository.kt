package uno.lux.mosaic.settings.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.DEFAULT_AUTO_PLAY_VIDEOS
import uno.lux.mosaic.settings.data.domain.Settings
import uno.lux.mosaic.settings.data.domain.ThemeMode

interface SettingsRepository {

    val settings: Flow<Settings>

    val themeMode: Flow<ThemeMode>
        get() = settings.map { it.themeMode }.distinctUntilChanged()

    val autoPlayVideos: Flow<Boolean>
        get() = settings.map { it.autoPlayVideos }.distinctUntilChanged()

    val language: Flow<AppLanguage?>
        get() = settings.map { it.language }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setAutoPlayVideos(enabled: Boolean)

    suspend fun setLanguage(language: AppLanguage)
}

class InMemorySettingsRepository(
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialAutoPlayVideos: Boolean = DEFAULT_AUTO_PLAY_VIDEOS,
    initialLanguage: AppLanguage? = null,
) : SettingsRepository {

    private val _settings = MutableStateFlow(
        Settings(
            themeMode = initialThemeMode,
            autoPlayVideos = initialAutoPlayVideos,
            language = initialLanguage,
        ),
    )
    override val settings: Flow<Settings> = _settings.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        _settings.update { it.copy(themeMode = mode) }
    }

    override suspend fun setAutoPlayVideos(enabled: Boolean) {
        _settings.update { it.copy(autoPlayVideos = enabled) }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        _settings.update { it.copy(language = language) }
    }
}
