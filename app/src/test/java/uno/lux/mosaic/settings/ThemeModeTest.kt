package uno.lux.mosaic.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.settings.data.domain.ThemeMode

class ThemeModeTest {

    @Test
    fun `light is never dark`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemInDark = true))
    }

    @Test
    fun `dark is always dark`() {
        assertTrue(ThemeMode.DARK.isDark(systemInDark = false))
    }

    @Test
    fun `system follows the device when light`() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDark = false))
    }

    @Test
    fun `system follows the device when dark`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDark = true))
    }

    @Test
    fun `fromName parses a known mode`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
    }

    @Test
    fun `fromName falls back to system for null`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
    }

    @Test
    fun `fromName falls back to system for an unknown value`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("nonsense"))
    }

    @Test
    fun `fromName round-trips every mode name`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromName(mode.name))
        }
    }
}
