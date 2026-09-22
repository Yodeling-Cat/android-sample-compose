package uno.lux.mosaic.designsystem.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * Pins the button's accessibility escape hatch. Holding is a motor-skill barrier, so a service
 * driving the button must be able to confirm outright — and must still be refused when the button
 * is disabled or busy, exactly as a finger would be.
 *
 * These are instrumented rather than plain-JVM because the thing under test *is* the semantics
 * tree: a JVM test could only assert against a stand-in for it.
 */
@RunWith(AndroidJUnit4::class)
class HoldToConfirmButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setButton(
        enabled: Boolean = true,
        isBusy: Boolean = false,
        onConfirm: () -> Unit,
    ) {
        composeRule.setContent {
            MosaicTheme {
                HoldToConfirmButton(
                    text = LABEL,
                    onConfirm = onConfirm,
                    enabled = enabled,
                    isBusy = isBusy,
                )
            }
        }
    }

    @Test
    fun anAccessibilityClickConfirmsWithoutHolding() {
        var confirmed = 0
        setButton { confirmed++ }

        composeRule
            .onNodeWithContentDescription(LABEL)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, confirmed)
    }

    @Test
    fun aDisabledButtonRefusesTheAccessibilityClick() {
        var confirmed = 0
        setButton(enabled = false) { confirmed++ }

        composeRule
            .onNodeWithContentDescription(LABEL)
            .assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(0, confirmed)
    }

    @Test
    fun aBusyButtonRefusesTheAccessibilityClick() {
        var confirmed = 0
        setButton(isBusy = true) { confirmed++ }

        composeRule
            .onNodeWithContentDescription(LABEL)
            .assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(0, confirmed)
    }

    /** The button names itself, so the two permanently-stacked labels aren't read back to back. */
    @Test
    fun theButtonExposesOneLabelWhicheverIsShowing() {
        setButton {}

        composeRule.onNodeWithContentDescription(LABEL).assertIsDisplayed()
        composeRule.onAllNodesWithText(LABEL).assertCountEquals(0)
    }
}

private const val LABEL = "Publish"
