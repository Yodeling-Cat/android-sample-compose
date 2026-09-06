package uno.lux.mosaic.app.util

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
 * Warms Coil's cache with the image *after* the one at [lastVisibleIndex], so scrolling or swiping
 * onto it lands on a decoded bitmap instead of an empty frame. An [ImageRequest] with no target
 * still runs through the singleton `ImageLoader` — the same one
 * [uno.lux.mosaic.app.MosaicApplication] gives `AsyncImage` — so what this stores is what the image
 * later reads.
 *
 * **[size] and [contentScale] must be what the image is actually drawn with, and getting this wrong
 * is worse than not prefetching at all.** Coil's memory-cache key is the URL alone (size joins it
 * only for a transformed request), so a prefetch and its `AsyncImage` share *one* entry, and whether
 * the stored bitmap is accepted is decided at lookup: `MemoryCacheService` compares its dimensions
 * against the request's, picking the ratio by [Scale] and the threshold by `Precision`. A prefetch
 * decoding under a different [Scale] therefore stores a bitmap the image rejects — which reloads and
 * overwrites the entry, which the next prefetch overwrites back, so every image reloads forever.
 * Compose resolves `ContentScale.Crop` to [Scale.FILL] and `Fit`/`Inside` to [Scale.FIT], the
 * mapping [asCoilScale] mirrors; taking a [ContentScale] rather than a [Scale] is what lets a call
 * site pass the very value it hands `AsyncImage`.
 *
 * [size] is the more forgiving of the two: `AsyncImage` requests `Precision.INEXACT` (it scales at
 * draw time), so a stored bitmap at *or above* the drawn size is reused.
 *
 * [lastVisibleIndex] is read inside a `snapshotFlow`, so it may read snapshot state directly — pass
 * `{ pagerState.currentPage }` for a pager, or the last entry of a lazy list's `layoutInfo`. Neither
 * it nor [urls] restarts the effect when it changes, so a list that is re-emitted on every like
 * toggle costs nothing.
 */
@Composable
internal fun PrefetchNextImage(
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
