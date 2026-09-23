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

/** Debounced, so a double-tapped back arrow cannot pop two pages. */
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
