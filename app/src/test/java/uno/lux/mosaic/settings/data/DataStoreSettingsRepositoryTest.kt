package uno.lux.mosaic.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
class DataStoreSettingsRepositoryTest {

    /**
     * In memory, not a temp file: DataStore commits by renaming over the file, which Windows
     * refuses on the second write.
     */
    private fun dataStore(): DataStore<Preferences> =
        InMemoryPreferencesDataStore()

    @Test
    fun `themeMode defaults to SYSTEM when nothing is persisted`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `setThemeMode persists the mode the flow then emits`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun `themeMode falls back to SYSTEM for an unrecognized persisted value`() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SOLARIZED" }
        val repository = DataStoreSettingsRepository(dataStore)

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `setThemeMode writes under the key the SharedPreferences migration fills`() = runTest {
        val dataStore = dataStore()
        val repository = DataStoreSettingsRepository(dataStore)

        repository.setThemeMode(ThemeMode.LIGHT)

        // "theme_mode" is the contract with the one-time SharedPreferences migration (the old
        // file used the same key), so a rename would silently drop every user's saved choice.
        assertEquals("LIGHT", dataStore.data.first()[stringPreferencesKey("theme_mode")])
    }

    @Test
    fun `autoPlayVideos defaults to off when nothing is persisted`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        assertEquals(false, repository.autoPlayVideos.first())
    }

    // Persists the value that is *not* the default, so a repository that ignored the write and
    // fell through to the default would fail rather than accidentally agree.
    @Test
    fun `setAutoPlayVideos persists the choice the flow then emits`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        repository.setAutoPlayVideos(true)

        assertEquals(true, repository.autoPlayVideos.first())
    }

    @Test
    fun `no language is persisted until one is chosen`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        assertEquals(null, repository.language.first())
    }

    @Test
    fun `setLanguage persists the choice the flow then emits`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        repository.setLanguage(AppLanguage.CZECH)

        assertEquals(AppLanguage.CZECH, repository.language.first())
    }

    @Test
    fun `settings carries every persisted value`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

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
    fun `settings defaults every field when nothing is persisted`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        assertEquals(Settings(), repository.settings.first())
    }

    @Test
    fun `a narrowed flow ignores a write to another setting`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        val seen = mutableListOf<ThemeMode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.themeMode.toList(seen)
        }

        repository.setAutoPlayVideos(true)

        assertEquals(listOf(ThemeMode.SYSTEM), seen)
    }
}
