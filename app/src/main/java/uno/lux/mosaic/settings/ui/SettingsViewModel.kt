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
import uno.lux.mosaic.settings.ui.SettingsUiEvent as UiEvent
import uno.lux.mosaic.settings.ui.SettingsUiState as UiState

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val navigator: Navigator,
) : ViewModel() {

    val uiState: StateFlow<UiState> = settingsRepository.settings
        .map { settings ->
            UiState.Content(
                themeMode = settings.themeMode,
                autoPlayVideos = settings.autoPlayVideos,
                language = settings.language ?: AppLanguage.Default,
            )
        }.stateInWhileSubscribed(viewModelScope, UiState.Loading)

    fun onEvent(event: UiEvent): Unit = when (event) {
        is UiEvent.SetThemeMode -> {
            setThemeMode(event.mode)
        }

        is UiEvent.SetAutoPlayVideos -> {
            setAutoPlayVideos(event.enabled)
        }

        is UiEvent.SetLanguage -> {
            setLanguage(event.language)
        }

        UiEvent.GoBack -> {
            navigator.goBack()
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
