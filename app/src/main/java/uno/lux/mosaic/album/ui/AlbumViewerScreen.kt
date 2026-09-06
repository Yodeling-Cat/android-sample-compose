package uno.lux.mosaic.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import uno.lux.mosaic.app.util.ImmersiveSystemBars
import uno.lux.mosaic.common.ui.OverlayBackButton
import kotlin.math.min

/**
 * Stateful entry point: binds the [AlbumViewerViewModel] (its sole job is routing back through
 * the `Navigator`) and forwards to the stateless overload below.
 */
@Composable
fun AlbumViewerScreen(
    imageUrls: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewerViewModel = hiltViewModel(),
) {
    AlbumViewerScreen(
        imageUrls = imageUrls,
        initialIndex = initialIndex,
        onBack = viewModel::goBack,
        modifier = modifier,
    )
}

/**
 * Full-screen album viewer: a black-background [HorizontalPager] that opens at [initialIndex]
 * and lets the user swipe between [imageUrls]'s images. Each page supports pinch-to-zoom — while
 * zoomed in, single-finger pan moves the image and the pager scroll is suppressed; at scale 1
 * single-finger swipes pass through to the pager as normal. Holding no ViewModel makes it
 * directly previewable and testable.
 */
@Composable
internal fun AlbumViewerScreen(
    imageUrls: List<String>,
    initialIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { imageUrls.size }

    ImmersiveSystemBars()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                url = imageUrls[page],
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (imageUrls.size > 1) {
            PageIndicator(
                pagerState = pagerState,
                total = imageUrls.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 20.dp),
            )
        }

        OverlayBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

/**
 * The "n / total" page counter pill. It reads [PagerState.currentPage] in its own scope, so
 * swiping between pages recomposes only this pill — not the pager, the zoom state, or the back
 * button — keeping the read off the screen's top-level scope.
 */
@Composable
private fun PageIndicator(
    pagerState: PagerState,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = "${pagerState.currentPage + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/**
 * An image that supports pinch-to-zoom (1×–5×). At scale = 1 single-finger events are NOT
 * consumed so the parent [HorizontalPager] can swipe normally. At scale > 1, or when two or
 * more fingers are down, position changes are consumed so the pager is locked.
 */
@Composable
private fun ZoomableImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isZoomingOrPanning = scale > 1f
                    if (isZoomingOrPanning) down.consume()

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) isZoomingOrPanning = true

                        if (isZoomingOrPanning) {
                            if (pressedCount > 0) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                val dz = newScale / scale

                                val viewSize = Size(size.width.toFloat(), size.height.toFloat())
                                val contentSize = if (imageSize.width > 0 && imageSize.height > 0) {
                                    val s = min(
                                        viewSize.width / imageSize.width,
                                        viewSize.height / imageSize.height,
                                    )
                                    Size(imageSize.width * s, imageSize.height * s)
                                } else {
                                    viewSize
                                }

                                val maxX =
                                    (contentSize.width * newScale - viewSize.width).coerceAtLeast(0f) /
                                        2f
                                val maxY =
                                    (contentSize.height * newScale - viewSize.height).coerceAtLeast(0f) /
                                        2f

                                // Anchor the zoom to the centroid so the point under the fingers
                                // stays fixed. Centroid is in composable coordinates; graphicsLayer
                                // pivots at the composable center, so adjust relative to that.
                                val c = centroid - Offset(size.width / 2f, size.height / 2f)
                                val rawOffset = offset * dz - c * (dz - 1f) + pan
                                offset = Offset(
                                    x = rawOffset.x.coerceIn(-maxX, maxX),
                                    y = rawOffset.y.coerceIn(-maxY, maxY),
                                )
                                scale = newScale
                                if (newScale <= 1f) offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { imageSize = it.painter.intrinsicSize },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
