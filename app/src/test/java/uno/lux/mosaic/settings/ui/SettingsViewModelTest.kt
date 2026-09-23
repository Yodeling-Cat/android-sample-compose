package uno.lux.mosaic.settings.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.settings.data.InMemorySettingsRepository
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : ViewModelTest() {

    private val backStack = backStackOf(Screen.Shell, Screen.Settings)
    private val navigator = Navigator().apply { attach(backStack) }

    private fun viewModel(
        repository: InMemorySettingsRepository = InMemorySettingsRepository(),
    ) = SettingsViewModel(repository, navigator)

    private fun TestScope.collecting(viewModel: SettingsViewModel) = viewModel.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private val SettingsViewModel.content: SettingsUiState.Content
        get() = uiState.value as SettingsUiState.Content

    @Test
    fun `state is Loading until the stored settings arrive`() {
        assertEquals(SettingsUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `themeMode reflects the repository`() = runTest {
        val viewModel = collecting(viewModel(InMemorySettingsRepository(ThemeMode.DARK)))

        assertEquals(ThemeMode.DARK, viewModel.content.themeMode)
    }

    @Test
    fun `SetThemeMode updates the exposed theme`() = runTest {
        val viewModel = collecting(viewModel())

        viewModel.onEvent(SettingsUiEvent.SetThemeMode(ThemeMode.LIGHT))

        assertEquals(ThemeMode.LIGHT, viewModel.content.themeMode)
    }

    @Test
    fun `autoPlayVideos reflects the repository`() = runTest {
        val viewModel = collecting(viewModel(InMemorySettingsRepository(initialAutoPlayVideos = true)))

        assertEquals(true, viewModel.content.autoPlayVideos)
    }

    // Turns on the setting that is off by default, so a dropped event fails rather than agrees.
    @Test
    fun `SetAutoPlayVideos updates the exposed preference`() = runTest {
        val viewModel = collecting(viewModel())

        viewModel.onEvent(SettingsUiEvent.SetAutoPlayVideos(true))

        assertEquals(true, viewModel.content.autoPlayVideos)
    }

    @Test
    fun `SetThemeMode leaves auto-play alone`() = runTest {
        val viewModel = collecting(viewModel(InMemorySettingsRepository(initialAutoPlayVideos = true)))

        viewModel.onEvent(SettingsUiEvent.SetThemeMode(ThemeMode.DARK))

        val expected = SettingsUiState.Content(
            themeMode = ThemeMode.DARK,
            autoPlayVideos = true,
            language = AppLanguage.Default,
        )
        assertEquals(expected, viewModel.content)
    }

    @Test
    fun `language reflects the repository`() = runTest {
        val repository = InMemorySettingsRepository(initialLanguage = AppLanguage.CZECH)
        val viewModel = collecting(viewModel(repository))

        assertEquals(AppLanguage.CZECH, viewModel.content.language)
    }

    // The picker needs an entry to mark as selected even in the moment before a launch pins one.
    @Test
    fun `a language nobody has chosen yet shows as the default`() = runTest {
        val viewModel = collecting(viewModel())

        assertEquals(AppLanguage.Default, viewModel.content.language)
    }

    @Test
    fun `SetLanguage updates the exposed language`() = runTest {
        val viewModel = collecting(viewModel())

        viewModel.onEvent(SettingsUiEvent.SetLanguage(AppLanguage.CZECH))

        assertEquals(AppLanguage.CZECH, viewModel.content.language)
    }

    @Test
    fun `GoBack pops the settings page`() {
        viewModel().onEvent(SettingsUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }
}
