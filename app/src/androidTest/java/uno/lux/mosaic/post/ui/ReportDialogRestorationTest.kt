package uno.lux.mosaic.post.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * The third kind of state this app carries across a restart, after the back stack and the
 * ViewModel-held drafts: **state a composable owns outright**. A report in progress belongs to no
 * ViewModel — the dialog holds the chosen reason and the typed details itself — so `rememberSaveable`
 * is all that stands between a rotation and starting the report over.
 *
 * [StateRestorationTester] performs the same save-and-rebuild round trip an activity recreation
 * does, and a rotation *is* an activity recreation, so this covers the rotation case without
 * driving a real orientation change.
 */
@RunWith(AndroidJUnit4::class)
class ReportDialogRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun keepsTheChosenReasonAndTypedDetailsAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MosaicTheme {
                ReportPostDialog(
                    sendState = ReportSendState.IDLE,
                    onDismiss = {},
                    onSubmit = { _, _ -> },
                )
            }
        }
        val spam = string(R.string.report_reason_spam)

        composeRule.onNodeWithText(spam).performClick()
        composeRule.onNodeWithText(string(R.string.report_details_hint)).performTextInput(DETAILS)

        restorationTester.emulateSavedInstanceStateRestore()

        // Both halves of the report survive: the radio choice this change made saveable, and the
        // details text, whose TextFieldState already was.
        composeRule.onNodeWithText(spam).assertIsSelected()
        composeRule.onNodeWithText(DETAILS).assertExists()
    }
}

private const val DETAILS = "Posted the same link four times."
