package uno.lux.mosaic.post.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportSendTest {
    @Test
    fun `the reported post reads the send state`() {
        val report = PostReportSend("p1", ReportSendState.SENDING)

        assertEquals(ReportSendState.SENDING, report.sendStateFor("p1"))
    }

    @Test
    fun `every other post reads IDLE`() {
        val report = PostReportSend("p1", ReportSendState.FAILED)

        assertEquals(ReportSendState.IDLE, report.sendStateFor("p2"))
    }

    @Test
    fun `with no report in flight every post reads IDLE`() {
        val report: PostReportSend? = null

        assertEquals(ReportSendState.IDLE, report.sendStateFor("p1"))
    }
}
