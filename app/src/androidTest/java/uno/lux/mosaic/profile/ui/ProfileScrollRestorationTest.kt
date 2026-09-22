package uno.lux.mosaic.profile.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.util.createActionsProxy
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.profile.data.domain.Profile

/**
 * The profile's scroll position is stored in two places, and only one of them was ever saved.
 * `rememberLazyListState` remembers where the posts list sits, but the *first* screenful of the
 * gesture never reaches the list at all — it collapses the header, and that offset lives in a
 * `mutableFloatStateOf` the screen owns outright. A profile scrolled less than one header's worth
 * therefore came back from a post detail (or a rotation) fully expanded, having remembered a list
 * position of zero perfectly.
 *
 * [StateRestorationTester] performs the same save-and-rebuild round trip an activity recreation
 * does — and leaving a page for another back-stack entry saves the same `rememberSaveable` state
 * through the entry decorator, so covering the recreation covers the navigation case with it.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScrollRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Held rather than built in the composition, so the screen sees one instance across the swipe's
    // recompositions — the proxy compares by identity, and a fresh one each pass would defeat that.
    private val actions = createActionsProxy<ProfileActions>()

    @Test
    fun keepsTheHeaderCollapsedAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MosaicTheme {
                ProfileScreen(
                    uiState = ProfileUiState.Loaded(profileData(), isCurrentUser = true),
                    isRefreshing = false,
                    failedAction = null,
                    reportSend = ReportSendState.IDLE,
                    onRefresh = {},
                    onRetry = {},
                    actions = actions,
                )
            }
        }
        val expandedTabsY = tabRowY()

        // The tab row rides up with the collapsing header until it pins beneath the app bar, so
        // where it sits is a direct read of how far the header is collapsed.
        composeRule.onRoot().performTouchInput { swipeUp() }
        val collapsedTabsY = tabRowY()
        assertTrue(
            "the swipe should have collapsed the header, moving the tabs up from $expandedTabsY",
            collapsedTabsY < expandedTabsY,
        )

        restorationTester.emulateSavedInstanceStateRestore()

        assertEquals(collapsedTabsY, tabRowY(), 0.5f)
    }

    private fun tabRowY(): Float =
        composeRule
            .onNodeWithText(string(R.string.profile_tab_posts))
            .fetchSemanticsNode()
            .positionInRoot
            .y

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}

private fun profileData(): ProfileScreenData {
    val user = SampleUsers.first()
    val posts = SamplePosts.filter { it.authorId == user.id }

    return ProfileScreenData(
        user = user,
        profile = Profile(userId = user.id, postsCount = posts.size),
        posts = posts,
    )
}
