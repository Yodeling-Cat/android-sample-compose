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
 * [size] and [contentScale] must match what the image is drawn with. The prefetch and its
 * `AsyncImage` share one memory-cache entry, and on a mismatch each reloads the other forever.
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

/** Duplicates Coil's `ContentScale.toScale`, which is `internal`. */
private fun ContentScale.asCoilScale(): Scale = when (this) {
    ContentScale.Fit, ContentScale.Inside -> Scale.FIT
    else -> Scale.FILL
}
