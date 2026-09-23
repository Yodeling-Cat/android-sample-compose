package uno.lux.mosaic.settings.ui

import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode

sealed interface SettingsUiEvent {
    data class SetThemeMode(
        val mode: ThemeMode,
    ) : SettingsUiEvent

    data class SetAutoPlayVideos(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetLanguage(
        val language: AppLanguage,
    ) : SettingsUiEvent

    data object GoBack : SettingsUiEvent
}
