package uno.lux.mosaic.common

import org.junit.Assert
import org.junit.Test

class FormattingTest {

    @Test
    fun `initials take the first letter of the first two words`() {
        Assert.assertEquals("AL", initials("Ada Lovelace"))
    }

    @Test
    fun `initials of a single word is one letter`() {
        Assert.assertEquals("L", initials("Linus"))
    }

    @Test
    fun `initials uppercase and ignore surrounding and repeated whitespace`() {
        Assert.assertEquals("GH", initials("  grace   hopper "))
    }

    @Test
    fun `initials of a blank name is empty`() {
        Assert.assertEquals("", initials("   "))
    }

    @Test
    fun `duration under a minute zero-pads the seconds`() {
        Assert.assertEquals("0:05", formatVideoDuration(5))
    }

    @Test
    fun `duration shows minutes and zero-padded seconds`() {
        Assert.assertEquals("1:35", formatVideoDuration(95))
    }

    @Test
    fun `duration keeps minutes un-padded below the hour`() {
        Assert.assertEquals("10:15", formatVideoDuration(615))
    }

    @Test
    fun `duration past an hour pads minutes and seconds`() {
        Assert.assertEquals("1:02:03", formatVideoDuration(3_723))
    }

    @Test
    fun `negative duration is coerced to zero`() {
        Assert.assertEquals("0:00", formatVideoDuration(-5))
    }
}
