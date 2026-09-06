package uno.lux.mosaic.settings.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.Settings
import uno.lux.mosaic.settings.data.domain.ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class InMemorySettingsRepositoryTest {

    @Test
    fun `defaults to the system theme`() = runTest {
        assertEquals(ThemeMode.SYSTEM, InMemorySettingsRepository().themeMode.first())
    }

    @Test
    fun `setThemeMode updates the exposed theme`() = runTest {
        val repository = InMemorySettingsRepository()

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun `auto-play is off by default`() = runTest {
        assertEquals(false, InMemorySettingsRepository().autoPlayVideos.first())
    }

    // Sets the value that is *not* the default, so a dropped write fails rather than agrees.
    @Test
    fun `setAutoPlayVideos updates the exposed preference`() = runTest {
        val repository = InMemorySettingsRepository()

        repository.setAutoPlayVideos(true)

        assertEquals(true, repository.autoPlayVideos.first())
    }

    @Test
    fun `no language is stored until one is chosen`() = runTest {
        assertEquals(null, InMemorySettingsRepository().language.first())
    }

    @Test
    fun `setLanguage updates the exposed language`() = runTest {
        val repository = InMemorySettingsRepository()

        repository.setLanguage(AppLanguage.CZECH)

        assertEquals(AppLanguage.CZECH, repository.language.first())
    }

    @Test
    fun `settings carries every stored value`() = runTest {
        val repository = InMemorySettingsRepository()

        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setAutoPlayVideos(true)
        repository.setLanguage(AppLanguage.CZECH)

        val expected = Settings(
            themeMode = ThemeMode.LIGHT,
            autoPlayVideos = true,
            language = AppLanguage.CZECH,
        )
        assertEquals(expected, repository.settings.first())
    }

    @Test
    fun `a write leaves the other setting alone`() = runTest {
        val repository = InMemorySettingsRepository(initialAutoPlayVideos = true)

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(true, repository.autoPlayVideos.first())
    }

    @Test
    fun `a narrowed flow ignores a write to another setting`() = runTest {
        val repository = InMemorySettingsRepository()
        val seen = mutableListOf<ThemeMode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.themeMode.toList(seen)
        }

        repository.setAutoPlayVideos(true)
        repository.setAutoPlayVideos(false)

        assertEquals(listOf(ThemeMode.SYSTEM), seen)
    }

    @Test
    fun `a narrowed flow emits a write to its own setting`() = runTest {
        val repository = InMemorySettingsRepository()
        val seen = mutableListOf<ThemeMode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.themeMode.toList(seen)
        }

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.DARK), seen)
    }

    @Test
    fun `a narrowed flow ignores a write that changes nothing`() = runTest {
        val repository = InMemorySettingsRepository(ThemeMode.DARK)
        val seen = mutableListOf<ThemeMode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.themeMode.toList(seen)
        }

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(listOf(ThemeMode.DARK), seen)
    }
}
