package uno.lux.mosaic.shell.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import uno.lux.mosaic.R
import uno.lux.mosaic.app.navigation.Screen

/**
 * The top-level entries of the navigation suite.
 *
 * Most are **tabs**: selecting one swaps the shell's content area and leaves the item
 * highlighted. An entry carrying a [screen] is instead an **action** — selecting it pushes that
 * page over the entire shell (covering the tab bar) and never becomes the selection, so the tab
 * the user was on stays highlighted underneath. That is how [CREATE] opens the composer, the
 * behavior a centred "new post" button has in apps like TikTok.
 */
enum class ShellDestinations(
    @get:StringRes val labelRes: Int,
    @get:DrawableRes val icon: Int,
    val screen: Screen? = null,
) {
    HOME(
        labelRes = R.string.nav_home,
        icon = R.drawable.ic_home,
    ),
    CREATE(
        labelRes = R.string.nav_create,
        icon = R.drawable.ic_add,
        screen = Screen.CreatePost,
    ),
    PROFILE(
        labelRes = R.string.nav_profile,
        icon = R.drawable.ic_account_box,
    ),
}
