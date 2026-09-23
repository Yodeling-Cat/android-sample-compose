package uno.lux.mosaic.shell.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import uno.lux.mosaic.R
import uno.lux.mosaic.app.navigation.Screen

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
