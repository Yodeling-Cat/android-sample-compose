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

    @Test
    fun theButtonExposesOneLabelWhicheverIsShowing() {
        setButton {}

        composeRule.onNodeWithContentDescription(LABEL).assertIsDisplayed()
        composeRule.onAllNodesWithText(LABEL).assertCountEquals(0)
    }
}

private const val LABEL = "Publish"
