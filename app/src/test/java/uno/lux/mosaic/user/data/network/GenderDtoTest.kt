package uno.lux.mosaic.user.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.user.data.domain.Gender

class GenderDtoTest {

    @Test
    fun `the multipart spelling is the one the server validates`() {
        assertEquals("Man", Gender.MAN.toDto().wireName)
        assertEquals("Woman", Gender.WOMAN.toDto().wireName)
    }
}
