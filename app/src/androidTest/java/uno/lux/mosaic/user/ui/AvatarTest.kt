package uno.lux.mosaic.user.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.user.data.domain.User

@RunWith(AndroidJUnit4::class)
class AvatarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val online =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.avatar_online)

    @Test
    fun marksAnOnlineUser() {
        composeRule.setContent {
            MosaicTheme {
                Avatar(user = User(id = "u1", nickname = "Ada", handle = "@ada", isOnline = true))
            }
        }

        composeRule.onNodeWithContentDescription(online).assertIsDisplayed()
    }

    @Test
    fun leavesAnOfflineUserUnmarked() {
        composeRule.setContent {
            MosaicTheme {
                Avatar(user = User(id = "u2", nickname = "Grace", handle = "@grace"))
            }
        }

        composeRule.onNodeWithContentDescription(online).assertDoesNotExist()
    }
}
