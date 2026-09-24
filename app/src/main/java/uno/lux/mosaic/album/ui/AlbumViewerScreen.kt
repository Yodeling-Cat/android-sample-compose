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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.common.ui.OverlayBackButton
import uno.lux.mosaic.common.util.ImmersiveSystemBars
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import kotlin.math.min
import uno.lux.mosaic.album.ui.AlbumViewerUiEvent as UiEvent

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
        onBack = { viewModel.onEvent(UiEvent.GoBack) },
        modifier = modifier,
    )
}

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
        OverlayBackButton(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

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
                currentPage = { pagerState.currentPage },
                total = imageUrls.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun PageIndicator(
    currentPage: () -> Int,
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
            text = "${currentPage() + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

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
                detectZoomAndPan(
                    scale = { scale },
                    offset = { offset },
                    imageSize = { imageSize },
                    onTransform = { newScale, newOffset ->
                        scale = newScale
                        offset = newOffset
                    },
                )
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

/**
 * At scale 1, single-finger events are left unconsumed so the parent [HorizontalPager] can swipe.
 * Once a gesture zooms or pans, it consumes every change until it ends.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    scale: () -> Float,
    offset: () -> Offset,
    imageSize: () -> Size,
    onTransform: (scale: Float, offset: Offset) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var isZoomingOrPanning = scale() > 1f
    if (isZoomingOrPanning) down.consume()

    do {
        val event = awaitPointerEvent()
        val pressedCount = event.changes.count { it.pressed }
        if (pressedCount >= 2) isZoomingOrPanning = true

        if (isZoomingOrPanning) {
            if (pressedCount > 0) {
                val currentScale = scale()
                val newScale = (currentScale * event.calculateZoom()).coerceIn(1f, 5f)
                val dz = newScale / currentScale

                val viewSize = Size(size.width.toFloat(), size.height.toFloat())
                val contentSize = contentSizeIn(viewSize, imageSize())
                val maxX = (contentSize.width * newScale - viewSize.width).coerceAtLeast(0f) / 2f
                val maxY = (contentSize.height * newScale - viewSize.height).coerceAtLeast(0f) / 2f

                // Anchor the zoom to the centroid so the point under the fingers stays fixed.
                // Centroid is in composable coordinates; graphicsLayer pivots at the composable
                // center, so adjust relative to that.
                val c = event.calculateCentroid() - Offset(size.width / 2f, size.height / 2f)
                val rawOffset = offset() * dz - c * (dz - 1f) + event.calculatePan()
                val newOffset = if (newScale <= 1f) {
                    Offset.Zero
                } else {
                    Offset(
                        x = rawOffset.x.coerceIn(-maxX, maxX),
                        y = rawOffset.y.coerceIn(-maxY, maxY),
                    )
                }

                onTransform(newScale, newOffset)
            }
            event.changes.forEach { it.consume() }
        }
    } while (event.changes.any { it.pressed })
}

private fun contentSizeIn(viewSize: Size, imageSize: Size): Size {
    if (imageSize.width <= 0 || imageSize.height <= 0) return viewSize

    val scale = min(viewSize.width / imageSize.width, viewSize.height / imageSize.height)

    return Size(imageSize.width * scale, imageSize.height * scale)
}

@Preview
@Composable
private fun AlbumViewerScreenPreview() {
    MosaicTheme {
        AlbumViewerScreen(
            imageUrls = SamplePosts.firstNotNullOf { it.album }.images,
            initialIndex = 1,
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PageIndicatorPreview() {
    MosaicTheme {
        PageIndicator(currentPage = { 2 }, total = 5)
    }
}
