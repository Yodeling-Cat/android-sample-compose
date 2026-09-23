package uno.lux.mosaic.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins that the back stack survives process death, and that two pushes of the same [Screen] stay
 * two pages, each with its own state, across the restore.
 *
 * [StateRestorationTester] does the save-and-rebuild of a real restart, but not the empty
 * in-memory stores that come with it; the ViewModel tests cover cold-start loading.
 *
 * The host mirrors `MosaicApp`'s wiring rather than using it, which would pull in Hilt and the
 * network.
 */
@RunWith(AndroidJUnit4::class)
class BackStackRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val navigator = Navigator()
    private var backStack: List<BackStackEntry> = emptyList()

    /** One of every [Screen], to catch a key that stops being serializable when a field is added. */
    private val everyScreen = listOf(
        Screen.Profile(userId = "u2"),
        Screen.Settings,
        Screen.EditProfile,
        Screen.CreatePost,
        Screen.PostDetail(postId = "p1"),
        Screen.FullscreenVideo(url = "https://example.test/v.mp4", title = "Clip"),
        Screen.AlbumViewer(images = listOf("https://example.test/1.jpg"), initialIndex = 0),
    )

    @Test
    fun restoresThePageThatWasOnTop() {
        val tester = startHost()

        push(Screen.Profile(userId = "u2"))
        assertTopIs(Screen.Profile(userId = "u2"))

        tester.emulateSavedInstanceStateRestore()

        assertTopIs(Screen.Profile(userId = "u2"))
    }

    @Test
    fun restoresTheEntriesUnderneathToo() {
        val tester = startHost()
        push(Screen.Profile(userId = "u2"))
        push(Screen.PostDetail(postId = "p1"))

        tester.emulateSavedInstanceStateRestore()

        // Backing out of the restored page walks the stack that was there before the restart,
        // rather than dropping straight to the root.
        assertTopIs(Screen.PostDetail(postId = "p1"))
        goBack()
        assertTopIs(Screen.Profile(userId = "u2"))
        goBack()
        assertTopIs(Screen.Shell)
    }

    @Test
    fun restoresEveryScreenKeyWithItsArguments() {
        val tester = startHost()
        everyScreen.forEach { push(it) }

        tester.emulateSavedInstanceStateRestore()

        composeTestRule.runOnIdle {
            assertEquals(listOf(Screen.Shell) + everyScreen, backStack.map { it.screen })
        }
    }

    /** The saveable-state decorator's half of the deal: state *inside* an entry comes back too. */
    @Test
    fun restoresSaveableStateHeldInsideAnEntry() {
        val tester = startHost()
        push(Screen.Profile(userId = "u2"))

        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()
        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("1")

        tester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("1")
    }

    /**
     * Two pushes of an equal [Screen] are two pages. State is scoped to [BackStackEntry.id], so
     * each push gets its own entry, and both survive a restore separately.
     */
    @Test
    fun keepsTwoEntriesOfTheSameScreenIndependent() {
        val tester = startHost()

        push(Screen.Profile(userId = "u2"))
        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()
        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()

        push(Screen.Profile(userId = "u2"))
        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("0")
        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()

        tester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("1")
        goBack()
        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("2")
    }

    /**
     * A screen that pins [Screen.sharedId] resolves every push to one entry, so its pushes share
     * one ViewModel store and one saveable state, before and after a restore. Unlike
     * `NavigatorTest`, this asserts on the state, not only on the ids.
     */
    @Test
    fun sharesStateBetweenEntriesOfAScreenThatPinsItsIdentity() {
        val tester = startHost()

        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()
        composeTestRule.onNodeWithTag(COUNTER_TAG).performClick()

        push(Screen.Shell)
        composeTestRule.runOnIdle { assertEquals(backStack[0].id, backStack[1].id) }
        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("2")

        tester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag(COUNTER_TAG).assertTextEquals("2")
    }

    private fun startHost(): StateRestorationTester {
        val tester = StateRestorationTester(composeTestRule)
        tester.setContent {
            TestNavHost(navigator = navigator, onBackStack = { backStack = it })
        }

        return tester
    }

    private fun push(screen: Screen) = composeTestRule.runOnIdle { navigator.goTo(screen) }

    private fun goBack() = composeTestRule.runOnIdle { navigator.goBack() }

    private fun assertTopIs(screen: Screen) {
        composeTestRule.onNodeWithTag(CURRENT_KEY_TAG).assertTextEquals(screen.toString())
    }
}

private const val CURRENT_KEY_TAG = "current_key"
private const val COUNTER_TAG = "counter"

/**
 * A stand-in for `MosaicApp`: the same composition-owned back stack, [Navigator] attachment and
 * entry decorators, with every key rendered as its own name instead of a real screen.
 */
@Composable
private fun TestNavHost(navigator: Navigator, onBackStack: (List<BackStackEntry>) -> Unit) {
    val backStack = rememberBackStack(navigator, root = Screen.Shell)

    // Handing the stack out from the effect, not the composition body, so the test reads the
    // instance that is actually attached — a restore brings a new one, and re-runs this.
    DisposableEffect(navigator, backStack) {
        navigator.attach(backStack)
        onBackStack(backStack)
        onDispose { navigator.detach(backStack) }
    }

    NavDisplay(
        backStack = backStack,
        onBack = navigator::goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // The app's own provider, so what these tests pin is the content key MosaicApp uses
        // rather than a second copy of the rule written here.
        entryProvider = backStackEntryProvider { screen -> KeyLabel(screen) },
    )
}

/** Names the entry on screen, and carries a saveable counter to prove per-entry state restores. */
@Composable
private fun KeyLabel(screen: Screen) {
    var taps by rememberSaveable { mutableIntStateOf(0) }

    Column {
        Text(text = screen.toString(), modifier = Modifier.testTag(CURRENT_KEY_TAG))
        Text(
            text = taps.toString(),
            modifier = Modifier
                .testTag(COUNTER_TAG)
                .clickable { taps++ },
        )
    }
}
