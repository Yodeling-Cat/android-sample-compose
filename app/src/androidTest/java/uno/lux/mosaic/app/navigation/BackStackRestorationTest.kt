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
 * Pins the guarantee the whole navigation design rests on: **the back stack survives process
 * death**. An app killed in the background comes back on the page it was killed on, because the
 * [BackStackEntry] keys are `@Serializable` and [rememberBackStack] saves them into the instance
 * state. It also pins the other half of an entry's identity — that two pushes of the same [Screen]
 * are two pages, each with its own state, on both sides of that restart.
 *
 * [StateRestorationTester.emulateSavedInstanceStateRestore] is what makes that testable without a
 * device kill: it saves the registry, throws the composition away, and rebuilds it from the saved
 * state — the same round trip the platform performs when it restarts a killed app. What it cannot
 * emulate is the *other* half of a real restart, that every in-memory store comes back empty; the
 * screens' own cold-start loading is covered by their ViewModel tests.
 *
 * The host below mirrors `MosaicApp`'s wiring — a [rememberBackStack] owned by the composition,
 * attached to a [Navigator], rendered through the same [backStackEntryProvider] — rather than
 * using it directly, which would drag in Hilt and the network for a question that is purely about
 * the keys.
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
     * The same page open twice is two pages. State is scoped to [BackStackEntry.id] rather than to
     * the [Screen], so a second push of an *equal* key gets its own entry — and both survive a
     * restore separately. Before that identity existed, the second push landed in the first one's
     * saveable-state slot and ViewModel store, and this counter would read 2 instead of 0.
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
     * The opt-in on the other side. A screen pinning [Screen.sharedId] resolves every push to one
     * identity, so its entries share a ViewModel store and one set of saveable state — what every
     * other screen deliberately gives up — and go on sharing it across a restore. Asserting on the
     * state rather than only on the ids is what this can say that `NavigatorTest` cannot.
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
