package uno.lux.mosaic.post.ui.report

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

@RunWith(AndroidJUnit4::class)
class ReportHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val snackbarHostState = SnackbarHostState()

    private val events = mutableListOf<ReportDialogEvent>()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun showsTheThanksInTheScreensSnackbarAndSpendsItOnceDismissed() {
        composeRule.setContent {
            MosaicTheme {
                ReportHost(
                    report = PostReport.Sent,
                    snackbarHostState = snackbarHostState,
                    onEvent = events::add,
                )
                SnackbarHost(snackbarHostState)
            }
        }

        composeRule.onNodeWithText(string(R.string.report_sent)).assertExists()
        assertEquals(emptyList<ReportDialogEvent>(), events)

        composeRule.runOnIdle { snackbarHostState.currentSnackbarData?.dismiss() }
        composeRule.waitForIdle()

        assertEquals(listOf(ReportDialogEvent.SentShown), events)
    }
}
