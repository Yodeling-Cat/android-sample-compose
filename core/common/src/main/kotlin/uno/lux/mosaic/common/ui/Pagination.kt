package uno.lux.mosaic.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import uno.lux.mosaic.common.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

private const val LOAD_MORE_PREFETCH = 3

/**
 * Holds off while [loadMoreFailed]: firing again by itself would retry for as long as the device is
 * offline. [LoadMoreFooter] owns the retry.
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    endReached: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentBlocked by rememberUpdatedState(endReached || loadMoreFailed)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            !currentBlocked && lastVisible >= info.totalItemsCount - LOAD_MORE_PREFETCH
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

@Composable
fun LoadMoreFooter(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!failed) {
        LoadingMoreFooter(modifier)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.error_load_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.error_retry))
        }
    }
}

@Composable
private fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadMoreFooterPreview() {
    MosaicTheme {
        Column {
            LoadMoreFooter(failed = false, onRetry = {})
            LoadMoreFooter(failed = true, onRetry = {})
        }
    }
}
