package uno.lux.mosaic.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.settings.data.AppLocaleRepository
import uno.lux.mosaic.settings.data.InMemoryAppLocaleRepository
import uno.lux.mosaic.settings.data.InMemorySettingsRepository
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode
import uno.lux.mosaic.testing.ViewModelTest

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : ViewModelTest() {

    private fun viewModel(
        settingsRepository: InMemorySettingsRepository = InMemorySettingsRepository(ThemeMode.DARK),
        appLocaleRepository: AppLocaleRepository = InMemoryAppLocaleRepository(settingsRepository),
    ) = MainViewModel(settingsRepository, appLocaleRepository, currentUserId = "u1")

    @Test
    fun `themeMode is null until the flow is collected`() {
        val viewModel = viewModel()

        assertEquals(null, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode reflects the repository`() = runTest {
        val repository = InMemorySettingsRepository(ThemeMode.DARK)
        val viewModel = viewModel(settingsRepository = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.themeMode.collect {}
        }

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        repository.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun `resolveInitialLanguage pins the device language on a first launch`() = runTest {
        val settings = InMemorySettingsRepository()
        val locales = InMemoryAppLocaleRepository(settings, systemLanguageTags = "cs-CZ,en-US")

        viewModel(settings, locales).resolveInitialAppLanguage()

        assertEquals(AppLanguage.CZECH, settings.language.first())
    }

    @Test
    fun `resolveInitialLanguage leaves a stored language alone`() = runTest {
        val settings = InMemorySettingsRepository(initialLanguage = AppLanguage.ENGLISH)
        val locales = InMemoryAppLocaleRepository(settings, systemLanguageTags = "cs-CZ")

        viewModel(settings, locales).resolveInitialAppLanguage()

        assertEquals(AppLanguage.ENGLISH, settings.language.first())
    }

    @Test
    fun `the stored language is applied to the platform`() = runTest {
        val settings = InMemorySettingsRepository(initialLanguage = AppLanguage.CZECH)
        val locales = InMemoryAppLocaleRepository(settings)

        viewModel(settings, locales)

        assertEquals(listOf(AppLanguage.CZECH), locales.applied)
    }

    @Test
    fun `a language change is applied to the platform`() = runTest {
        val settings = InMemorySettingsRepository(initialLanguage = AppLanguage.ENGLISH)
        val locales = InMemoryAppLocaleRepository(settings)
        viewModel(settings, locales)

        settings.setLanguage(AppLanguage.CZECH)

        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.CZECH), locales.applied)
    }

    // Nothing is in effect yet on a first launch; seeding the choice is what drives the apply.
    @Test
    fun `no language is applied until one is stored`() = runTest {
        val settings = InMemorySettingsRepository()
        val locales = InMemoryAppLocaleRepository(settings)

        viewModel(settings, locales)

        assertEquals(emptyList<AppLanguage>(), locales.applied)
    }
}
