package uno.lux.mosaic.post.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.designsystem.theme.MosaicTheme

@RunWith(AndroidJUnit4::class)
class ReportDialogInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun handsTheChosenReasonAndTrimmedDetailsToTheHost() {
        var reported: Pair<ReportReason, String>? = null
        composeRule.setContent {
            MosaicTheme {
                ReportPostDialog(
                    sendState = ReportSendState.IDLE,
                    onDismiss = {},
                    onSubmit = { reason, details -> reported = reason to details },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.report_reason_hate_speech)).performClick()
        composeRule
            .onNodeWithText(string(R.string.report_details_hint))
            .performTextInput("  Read the third paragraph  ")
        composeRule.onNodeWithText(string(R.string.report_send)).performClick()

        assertEquals(ReportReason.HATE_SPEECH to "Read the third paragraph", reported)
    }
}
