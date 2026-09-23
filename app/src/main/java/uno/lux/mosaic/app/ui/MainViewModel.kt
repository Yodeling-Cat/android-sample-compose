package uno.lux.mosaic.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import uno.lux.mosaic.app.di.CurrentUserId
import uno.lux.mosaic.common.util.launch
import uno.lux.mosaic.common.util.stateInWhileSubscribed
import uno.lux.mosaic.settings.data.AppLocaleRepository
import uno.lux.mosaic.settings.data.SettingsRepository
import uno.lux.mosaic.settings.data.domain.ThemeMode
import uno.lux.mosaic.user.data.domain.UserId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLocaleRepository: AppLocaleRepository,
    @param:CurrentUserId val currentUserId: UserId,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateInWhileSubscribed(viewModelScope, initialValue = null)

    init {
        observeAppLanguage()
    }

    /**
     * First launch only: adopts the device's language if we ship it. Called from
     * the Activity because the locale APIs need AppCompat's delegate to
     * have attached — which is only guaranteed once `super.onCreate` has run.
     */
    fun resolveInitialAppLanguage() = launch {
        appLocaleRepository.resolveInitialLanguage()
    }

    private fun observeAppLanguage() = launch {
        settingsRepository.language.filterNotNull().collect(appLocaleRepository::applyLanguage)
    }
}
