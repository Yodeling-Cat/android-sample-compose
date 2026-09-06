package uno.lux.mosaic.app.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.app.theme.MosaicTheme

/**
 * Pins what [contentDescription] being optional actually means to a screen reader.
 *
 * An icon-only button with no description is an unlabelled node rather than one labelled with the
 * drawable's name, so the default has to be a deliberate choice at every call site — these are
 * instrumented because the semantics tree is the thing under test.
 */
@RunWith(AndroidJUnit4::class)
class AppBarActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelsTheActionWithTheDescriptionItIsGiven() {
        var clicks = 0
        composeRule.setContent {
            MosaicTheme {
                AppBarAction(
                    icon = R.drawable.ic_settings,
                    onClick = { clicks++ },
                    contentDescription = "Settings",
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun carriesNoDescriptionWhenNoneIsGiven() {
        composeRule.setContent {
            MosaicTheme {
                AppBarAction(icon = R.drawable.ic_settings, onClick = {})
            }
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertDoesNotExist()
    }
}
