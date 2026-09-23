package uno.lux.mosaic.post.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.designsystem.theme.MosaicTheme

@RunWith(AndroidJUnit4::class)
class PostActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun heartDescriptionFollowsTheLike() {
        var post by mutableStateOf(SamplePosts.first { !it.isLiked })
        var toggles = 0
        composeRule.setContent {
            MosaicTheme {
                PostActions(
                    post = post,
                    onToggleLike = {
                        toggles++
                        post = post.copy(isLiked = !post.isLiked)
                    },
                    onToggleBookmark = {},
                    onCommentClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.post_action_like)).performClick()

        assertEquals(1, toggles)
        composeRule.onNodeWithContentDescription(string(R.string.post_action_unlike)).assertIsDisplayed()
    }
}
