package uno.lux.mosaic.post.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class PostReporterTest {

    private val dataSource = FakePostDataSource()

    private fun CoroutineScope.reporter() =
        PostReporter(this, PostRepository(dataSource, UserRepository(FakeUserDataSource())))

    @Test
    fun `nothing is open until a post is reported`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(backgroundScope.reporter().report.value)
    }

    @Test
    fun `Open opens an idle dialog on the post`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()

        reporter.open("p1")

        assertEquals(PostReport.Open("p1", ReportSendState.IDLE), reporter.report.value)
    }

    // The dialog stays up for the whole request, and closes only once the server has the report —
    // which is the moment the thanks stops being a guess.
    @Test
    fun `a report is SENDING while it is out and Sent once the server has it`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()
        var whileOut: PostReport? = null
        dataSource.whileInFlight = { whileOut = reporter.report.value }
        reporter.open("p1")

        reporter.send(ReportReason.SPAM, "Four times")

        assertEquals(PostReport.Open("p1", ReportSendState.SENDING), whileOut)
        assertEquals(PostReport.Sent, reporter.report.value)
        assertEquals(listOf(FakePostDataSource.Report("p1", ReportReason.SPAM, "Four times")), dataSource.reports)
    }

    // A snackbar would be announced behind the dialog's scrim, so a failure is stated in it.
    @Test
    fun `a failed report keeps the dialog open and says so`() = runTest(UnconfinedTestDispatcher()) {
        dataSource.reportError = UnknownHostException("offline")
        val reporter = backgroundScope.reporter()
        reporter.open("p1")

        reporter.send(ReportReason.SPAM, "")

        assertEquals(PostReport.Open("p1", ReportSendState.FAILED), reporter.report.value)
    }

    @Test
    fun `a report can be sent again after it failed`() = runTest(UnconfinedTestDispatcher()) {
        dataSource.reportError = UnknownHostException("offline")
        val reporter = backgroundScope.reporter()
        reporter.open("p1")
        reporter.send(ReportReason.SPAM, "")
        dataSource.reportError = null

        reporter.send(ReportReason.SPAM, "")

        assertEquals(PostReport.Sent, reporter.report.value)
        assertEquals(listOf(FakePostDataSource.Report("p1", ReportReason.SPAM, "")), dataSource.reports)
    }

    @Test
    fun `a second Send while the first is out is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val held = CompletableDeferred<Unit>()
        dataSource.whileInFlight = { held.await() }
        val reporter = backgroundScope.reporter()
        reporter.open("p1")

        reporter.send(ReportReason.SPAM, "")
        reporter.send(ReportReason.SPAM, "")
        held.complete(Unit)

        assertEquals(listOf(FakePostDataSource.Report("p1", ReportReason.SPAM, "")), dataSource.reports)
    }

    @Test
    fun `Send with no dialog open sends nothing`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()

        reporter.send(ReportReason.SPAM, "")

        assertNull(reporter.report.value)
        assertEquals(emptyList<FakePostDataSource.Report>(), dataSource.reports)
    }

    @Test
    fun `Close closes the dialog`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()
        reporter.open("p1")

        reporter.close()

        assertNull(reporter.report.value)
    }

    // Closing mid-send abandons the report with the dialog. Otherwise its answer would still land,
    // and thank the user for — or fail at them over — a report they walked away from.
    @Test
    fun `a report closed while it is out cannot settle after the dialog`() = runTest(UnconfinedTestDispatcher()) {
        val held = CompletableDeferred<Unit>()
        dataSource.whileInFlight = { held.await() }
        val reporter = backgroundScope.reporter()
        reporter.open("p1")
        reporter.send(ReportReason.SPAM, "")

        reporter.close()
        held.complete(Unit)
        advanceUntilIdle()

        assertNull(reporter.report.value)
        assertEquals(emptyList<FakePostDataSource.Report>(), dataSource.reports)
    }

    @Test
    fun `SentShown spends the thanks`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()
        reporter.open("p1")
        reporter.send(ReportReason.SPAM, "")

        reporter.sentShown()

        assertNull(reporter.report.value)
    }

    // The thanks is spent after the dialog for the next report may already be up; spending it
    // must not close that dialog too.
    @Test
    fun `SentShown leaves a dialog opened since alone`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = backgroundScope.reporter()
        reporter.open("p1")
        reporter.send(ReportReason.SPAM, "")
        reporter.open("p2")

        reporter.sentShown()

        assertEquals(PostReport.Open("p2", ReportSendState.IDLE), reporter.report.value)
    }
}
