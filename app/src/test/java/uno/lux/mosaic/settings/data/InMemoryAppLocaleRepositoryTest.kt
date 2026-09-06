package uno.lux.mosaic.settings.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.settings.data.domain.AppLanguage

class InMemoryAppLocaleRepositoryTest {

    private fun repository(
        settingsRepository: SettingsRepository = InMemorySettingsRepository(),
        systemLanguageTags: String = "",
    ) = InMemoryAppLocaleRepository(settingsRepository, systemLanguageTags)

    @Test
    fun `apply passes the language to the platform`() {
        val repository = repository()

        repository.applyLanguage(AppLanguage.CZECH)

        assertEquals(listOf(AppLanguage.CZECH), repository.applied)
    }

    // Applying recreates the Activity, so re-applying what is in effect would loop the collector.
    @Test
    fun `apply ignores the language already in effect`() {
        val repository = repository()

        repository.applyLanguage(AppLanguage.CZECH)
        repository.applyLanguage(AppLanguage.CZECH)

        assertEquals(listOf(AppLanguage.CZECH), repository.applied)
    }

    @Test
    fun `on first launch the device's language is stored when we ship it`() = runTest {
        val settings = InMemorySettingsRepository()

        repository(settings, systemLanguageTags = "cs-CZ").resolveInitialLanguage()

        assertEquals(AppLanguage.CZECH, settings.settings.first().language)
    }

    @Test
    fun `on first launch a device language we don't ship falls back to English`() = runTest {
        val settings = InMemorySettingsRepository()

        repository(settings, systemLanguageTags = "de-DE,sk-SK").resolveInitialLanguage()

        assertEquals(AppLanguage.ENGLISH, settings.settings.first().language)
    }

    @Test
    fun `on first launch the best of the device's preferred languages wins`() = runTest {
        val settings = InMemorySettingsRepository()

        repository(settings, systemLanguageTags = "de-DE,cs-CZ,en-US").resolveInitialLanguage()

        assertEquals(AppLanguage.CZECH, settings.settings.first().language)
    }

    @Test
    fun `resolveInitialLanguage leaves a stored language alone`() = runTest {
        val settings = InMemorySettingsRepository(initialLanguage = AppLanguage.ENGLISH)

        repository(settings, systemLanguageTags = "cs-CZ").resolveInitialLanguage()

        assertEquals(AppLanguage.ENGLISH, settings.settings.first().language)
    }

    @Test
    fun `resolveInitialLanguage pins its choice, so a later call cannot re-resolve it`() = runTest {
        val settings = InMemorySettingsRepository()
        val repository = repository(settings, systemLanguageTags = "de-DE")

        repository.resolveInitialLanguage()
        settings.setLanguage(AppLanguage.CZECH)
        repository.resolveInitialLanguage()

        assertEquals(AppLanguage.CZECH, settings.settings.first().language)
    }

    // Seeding is a write to the store, not a poke at the platform: the collector applies it.
    @Test
    fun `resolveInitialLanguage applies nothing itself`() = runTest {
        val repository = repository(systemLanguageTags = "cs-CZ")

        repository.resolveInitialLanguage()

        assertEquals(emptyList<AppLanguage>(), repository.applied)
    }
}
