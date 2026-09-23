package uno.lux.mosaic.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uno.lux.mosaic.designsystem.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * One icon affordance in a top app bar — the navigation icon, or one of the trailing actions.
 *
 * The click is debounced, which is the reason to reach for this over a bare [IconButton]: a
 * double-tapped back arrow must not pop two pages, and every other navigation control in the app
 * carries the same 500 ms guard.
 *
 * [contentDescription] is null by default, for an icon whose meaning something else on screen
 * already carries. Pass one whenever this button is the only thing saying what it does — which is
 * nearly always, since an unlabelled icon is invisible to a screen reader.
 */
@Composable
fun AppBarAction(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    IconButton(onClick = onClick.rememberDebounced(), modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppBarActionPreview() {
    MosaicTheme {
        AppBarAction(
            icon = R.drawable.ic_app,
            onClick = {},
            contentDescription = stringResource(R.string.app_name),
        )
    }
}
