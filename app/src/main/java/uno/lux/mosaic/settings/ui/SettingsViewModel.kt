package uno.lux.mosaic.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.common.util.stateInWhileSubscribed
import uno.lux.mosaic.settings.data.SettingsRepository
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val navigator: Navigator,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { settings ->
            SettingsUiState.Content(
                themeMode = settings.themeMode,
                autoPlayVideos = settings.autoPlayVideos,
                language = settings.language ?: AppLanguage.Default,
            )
        }.stateInWhileSubscribed(viewModelScope, SettingsUiState.Loading)

    fun eventSink(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetThemeMode -> {
                setThemeMode(event.mode)
            }

            is SettingsUiEvent.SetAutoPlayVideos -> {
                setAutoPlayVideos(event.enabled)
            }

            is SettingsUiEvent.SetLanguage -> {
                setLanguage(event.language)
            }

            SettingsUiEvent.GoBack -> {
                navigator.goBack()
            }
        }
    }

    private fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    private fun setAutoPlayVideos(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPlayVideos(enabled) }
    }

    private fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }
}
