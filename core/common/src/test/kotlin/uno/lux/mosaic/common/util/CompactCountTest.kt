package uno.lux.mosaic.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactCountTest {

    @Test
    fun `counts below a thousand are exact`() {
        assertEquals(CompactCount.Ones("999"), compactCount(999))
    }

    @Test
    fun `thousands keep one decimal place`() {
        assertEquals(CompactCount.Thousands("1.2"), compactCount(1_200))
    }

    @Test
    fun `whole thousands drop the decimal`() {
        assertEquals(CompactCount.Thousands("12"), compactCount(12_000))
    }

    @Test
    fun `millions are scaled`() {
        assertEquals(CompactCount.Millions("1"), compactCount(1_000_000))
    }

    @Test
    fun `negative counts are coerced to zero`() {
        assertEquals(CompactCount.Ones("0"), compactCount(-5))
    }
}
