package uno.lux.mosaic.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size

/**
 * Warms Coil's cache with the image after the one at [lastVisibleIndex], so scrolling onto it
 * shows a decoded bitmap instead of an empty frame.
 *
 * **[size] and [contentScale] must match what the image is drawn with.** The prefetch and its
 * `AsyncImage` share one memory-cache entry keyed by URL, and Coil rejects a cached bitmap decoded
 * under a different [Scale]. On a mismatch the two overwrite each other and every image reloads
 * forever. [size] is more forgiving: a bitmap at or above the drawn size is reused.
 *
 * [lastVisibleIndex] is read in a `snapshotFlow`, so it may read snapshot state directly. A change
 * to it or to [urls] does not restart the effect.
 */
@Composable
fun PrefetchNextImage(
    urls: List<String>,
    size: Size,
    contentScale: ContentScale,
    lastVisibleIndex: () -> Int,
) {
    val context = LocalPlatformContext.current
    val scale = contentScale.asCoilScale()
    val currentUrls by rememberUpdatedState(urls)
    val currentIndex by rememberUpdatedState(lastVisibleIndex)

    LaunchedEffect(context, size, scale) {
        snapshotFlow { currentIndex() }.collect { lastVisible ->
            val next = currentUrls.getOrNull(lastVisible + 1) ?: return@collect

            val request = ImageRequest
                .Builder(context)
                .data(next)
                .size(size)
                .scale(scale)
                .build()

            SingletonImageLoader
                .get(context)
                .enqueue(request)
        }
    }
}

/**
 * The mapping Coil's Compose layer applies internally (`ContentScale.toScale`), duplicated because
 * that function is `internal` to the library: a prefetch has to name the [Scale] its `AsyncImage`
 * will ask for, and no public API exposes it.
 */
private fun ContentScale.asCoilScale(): Scale = when (this) {
    ContentScale.Fit, ContentScale.Inside -> Scale.FIT
    else -> Scale.FILL
}
