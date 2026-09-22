package uno.lux.mosaic.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_PREFETCH = 3

/**
 * Fires [onLoadMore] when the list is scrolled within [LOAD_MORE_PREFETCH] items of the end and
 * [endReached] is false. Both [endReached] and [onLoadMore] are captured via [rememberUpdatedState]
 * so the effect restarts only when [listState] changes, not on every recomposition.
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    endReached: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentEndReached by rememberUpdatedState(endReached)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            !currentEndReached && lastVisible >= info.totalItemsCount - LOAD_MORE_PREFETCH
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/** Spinner shown as the last list item while the next page is being fetched. */
@Composable
fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
