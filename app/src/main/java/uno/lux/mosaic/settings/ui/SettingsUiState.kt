package uno.lux.mosaic.settings.ui

import androidx.compose.runtime.Immutable
import uno.lux.mosaic.settings.domain.AppLanguage
import uno.lux.mosaic.settings.domain.ThemeMode

@Immutable
sealed interface SettingsUiState {

    data object Loading : SettingsUiState

    data class Content(
        val themeMode: ThemeMode,
        val autoPlayVideos: Boolean,
        val language: AppLanguage,
    ) : SettingsUiState
}
