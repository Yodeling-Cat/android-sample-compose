package uno.lux.mosaic.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uno.lux.mosaic.settings.data.domain.AppLanguage

class AppLanguageTest {

    @Test
    fun `a bare language tag resolves to its language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
        assertEquals(AppLanguage.CZECH, AppLanguage.fromLanguageTags("cs"))
    }

    @Test
    fun `a region subtag is ignored`() {
        assertEquals(AppLanguage.CZECH, AppLanguage.fromLanguageTags("cs-CZ"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-GB"))
    }

    @Test
    fun `an uppercase tag resolves`() {
        assertEquals(AppLanguage.CZECH, AppLanguage.fromLanguageTags("CS"))
    }

    @Test
    fun `the first shipped language in the list wins`() {
        assertEquals(AppLanguage.CZECH, AppLanguage.fromLanguageTags("cs-CZ,en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US,cs-CZ"))
    }

    @Test
    fun `languages we don't ship are skipped over, not matched`() {
        assertEquals(AppLanguage.CZECH, AppLanguage.fromLanguageTags("de-DE,sk-SK,cs-CZ,en"))
    }

    // The device's language list carries Unicode extension subtags (first-day-of-week, measurement
    // system). Verbatim from a Pixel 9 Pro set to English with Czech as its second language.
    @Test
    fun `unicode extension subtags are ignored`() {
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguage.fromLanguageTags("en-US-u-fw-mon-mu-celsius,cs-CZ-u-fw-mon-mu-celsius"),
        )
        assertEquals(
            AppLanguage.CZECH,
            AppLanguage.fromLanguageTags("cs-CZ-u-fw-mon-mu-celsius,en-US-u-fw-mon-mu-celsius"),
        )
    }

    @Test
    fun `a list naming nothing we ship resolves to null`() {
        assertNull(AppLanguage.fromLanguageTags("de-DE,sk-SK"))
    }

    @Test
    fun `an empty or absent list resolves to null`() {
        assertNull(AppLanguage.fromLanguageTags(""))
        assertNull(AppLanguage.fromLanguageTags(null))
    }

    @Test
    fun `the fallback for an unshipped device language is English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.Default)
    }
}
