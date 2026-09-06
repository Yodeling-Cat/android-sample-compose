package uno.lux.mosaic.post.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.size.Size
import uno.lux.mosaic.R
import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.app.theme.MosaicGradients
import uno.lux.mosaic.app.util.PrefetchNextImage
import uno.lux.mosaic.app.util.debouncedClickable
import uno.lux.mosaic.common.ui.MediaBadge

/**
 * How a gallery tile draws its photo, shared by the rendered image and its prefetch request — the
 * prefetch has to decode at the same size *and* scale or its cache entry is one the image rejects.
 */
private val TileWidth = 240.dp
private val TileHeight = 180.dp
private val TileContentScale = ContentScale.Crop

/**
 * An album post's media: its [Album.images] laid out in a horizontally scrolling row, each photo
 * loaded with Coil. Tapping an image calls [onOpenImage] with its index (for the fullscreen
 * viewer). Each photo carries a position badge ("2 / 3") with an image icon in its top-end corner.
 * While a photo loads (or if it fails) the deterministic Mosaic gradient shows through behind it.
 *
 * The photo *after* the last visible one is warmed ahead of the scroll by [PrefetchNextImage], at
 * the tile's own pixel size and [TileContentScale] so the entry it stores is the one [AsyncImage]
 * accepts.
 */
@Composable
internal fun AlbumPostGallery(
    album: Album,
    onOpenImage: (imageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val tileSize = remember(density) {
        with(density) { Size(width = TileWidth.roundToPx(), height = TileHeight.roundToPx()) }
    }

    PrefetchNextImage(
        urls = album.images,
        size = tileSize,
        contentScale = TileContentScale,
    ) {
        val lastItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()

        lastItem?.index ?: -1
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(album.images) { index, url ->
            Box(
                modifier = Modifier
                    .width(TileWidth)
                    .height(TileHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MosaicGradients.mediaBrush("${album.id}-$index"))
                    .debouncedClickable { onOpenImage(index) },
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = album.title,
                    contentScale = TileContentScale,
                    modifier = Modifier.fillMaxSize(),
                )
                MediaBadge(
                    text = "${index + 1}/${album.itemCount}",
                    iconRes = R.drawable.ic_image,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 4.dp),
                )
            }
        }
    }
}
