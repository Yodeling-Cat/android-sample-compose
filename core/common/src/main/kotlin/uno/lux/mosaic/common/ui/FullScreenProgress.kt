package uno.lux.mosaic.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * Full-screen loading state: a centered progress indicator. The counterpart to [FullScreenError]
 * — used by screens whose initial data load is still in flight and left nothing to show. An
 * indicator that merely accompanies content (a footer paging spinner, a saving app-bar button)
 * is a different thing and stays where it is.
 */
@Composable
fun FullScreenProgress(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun FullScreenProgressPreview() {
    MosaicTheme {
        FullScreenProgress()
    }
}
