package uno.lux.mosaic.profile.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

@RunWith(AndroidJUnit4::class)
class ProfileTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Both on-demand tabs go through one call site, so this pins that each still gets its own
    // "shown" effect when the user switches straight from one to the other.
    @Test
    fun switchingFromLikesToSavedAsksForTheBookmarks() {
        val events = mutableListOf<ProfileUiEvent>()
        composeRule.setContent {
            MosaicTheme {
                ProfileScreen(
                    uiState = ProfileUiState.Loaded(profileData(), isCurrentUser = true),
                    isRefreshing = false,
                    failedAction = null,
                    report = null,
                    onEvent = events::add,
                )
            }
        }

        selectTab(R.string.profile_tab_likes)
        selectTab(R.string.profile_tab_saved)
        composeRule.waitForIdle()

        assertEquals(
            listOf(ProfileUiEvent.LikesTabShown, ProfileUiEvent.BookmarksTabShown),
            events.filter { it == ProfileUiEvent.LikesTabShown || it == ProfileUiEvent.BookmarksTabShown },
        )
    }

    private fun selectTab(labelRes: Int) {
        composeRule.onNode(hasText(string(labelRes)) and isSelectable()).performClick()
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
