package uno.lux.mosaic.user.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.user.data.domain.Gender

class GenderDtoTest {

    @Test
    fun `the multipart spelling is the one the server validates`() {
        assertEquals("Man", GenderDtoMapper.map(Gender.MAN).wireName)
        assertEquals("Woman", GenderDtoMapper.map(Gender.WOMAN).wireName)
    }
}
