package uno.lux.mosaic.settings.ui

import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode

// TODO: Here we use an event sink, but we don't do that on other screen, choose a single approach.
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
