package uno.lux.mosaic.settings.ui

import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode

sealed interface SettingsUiState {

    data object Loading : SettingsUiState

    data class Content(
        val themeMode: ThemeMode,
        val autoPlayVideos: Boolean,
        val language: AppLanguage,
    ) : SettingsUiState
}
