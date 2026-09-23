package uno.lux.mosaic.shell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * Back from any tab but Home returns to Home before it leaves the shell.
 *
 * It belongs in an instrumented test because the system back press is dispatched by the Activity,
 * which a JVM test has no way to raise.
 */
@RunWith(AndroidJUnit4::class)
class ShellBackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backFromAnotherTabReturnsHome() {
        var current by mutableStateOf(ShellDestinations.PROFILE)
        composeRule.setContent {
            MosaicTheme {
                ShellScreen(
                    currentDestination = current,
                    onSelectDestination = { current = it },
                    tabContent = {},
                )
            }
        }

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(ShellDestinations.HOME, current)
    }
}
