package uno.lux.mosaic.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.FailedActionEffect
import uno.lux.mosaic.common.ui.FullScreenError
import uno.lux.mosaic.common.ui.FullScreenProgress
import uno.lux.mosaic.common.ui.LoadMoreEffect
import uno.lux.mosaic.common.ui.LoadMoreFooter
import uno.lux.mosaic.designsystem.components.AppBarAction
import uno.lux.mosaic.designsystem.components.MosaicWordmark
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.ui.PostCard
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.post.ui.PostReport
import uno.lux.mosaic.post.ui.ReportHost
import uno.lux.mosaic.post.ui.cardContentType
import uno.lux.mosaic.video.ui.LocalVideoPlayback
import uno.lux.mosaic.common.R as CommonR
import uno.lux.mosaic.home.ui.HomeUiEvent as UiEvent
import uno.lux.mosaic.home.ui.HomeUiState as UiState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsStateWithLifecycle()
    val failedAction by viewModel.failedAction.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        autoPlayVideos = autoPlayVideos,
        failedAction = failedAction,
        report = report,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: UiState,
    isRefreshing: Boolean,
    autoPlayVideos: Boolean,
    failedAction: FailedAction?,
    report: PostReport?,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // A refresh that failed over a feed already on screen — announced without taking the posts
    // away. The duration is stated because Material's default is Indefinite whenever an action
    // label is given: right for a decision the user must make, wrong for a feed that is still
    // perfectly readable behind it.
    val refreshErrorMessage = (uiState as? UiState.Feed)?.refreshError?.asText()
    val retryLabel = stringResource(CommonR.string.error_retry)

    LaunchedEffect(refreshErrorMessage) {
        if (refreshErrorMessage == null) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = refreshErrorMessage,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        )
        // Spent, whether it was acted on or waited out — neither the message nor a rotation
        // rebuilding this composition should say it twice.
        onEvent(UiEvent.RefreshErrorShown)
        if (result == SnackbarResult.ActionPerformed) onEvent(UiEvent.Refresh)
    }

    // A delete whose request failed after its dialog already closed.
    FailedActionEffect(failedAction, snackbarHostState) { onEvent(UiEvent.FailedActionShown) }

    ReportHost(
        report = report,
        onSend = { reason, details -> onEvent(UiEvent.SendReport(reason, details)) },
        onClose = { onEvent(UiEvent.CloseReport) },
        onSentShown = { onEvent(UiEvent.ReportSentShown) },
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // The bar is pinned; its shadow shows while the list is scrolled away from the top.
            // canScrollBackward only changes when crossing the top, so it needs no derivedStateOf,
            // and reading it here keeps the invalidation inside this slot.
            FeedTopBar(
                elevated = listState.canScrollBackward,
                onOpenSettings = { onEvent(UiEvent.OpenSettings) },
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { onEvent(UiEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (uiState) {
                UiState.Loading -> {
                    FullScreenProgress()
                }

                is UiState.Error -> {
                    FullScreenError(
                        message = uiState.error.asText(),
                        onRetry = { onEvent(UiEvent.Retry) },
                    )
                }

                is UiState.Feed -> {
                    if (uiState.posts.isEmpty()) {
                        EmptyState()
                    } else {
                        FeedList(
                            posts = uiState.posts,
                            endReached = uiState.endReached,
                            loadMoreFailed = uiState.loadMoreFailed,
                            autoPlayVideos = autoPlayVideos,
                            listState = listState,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTopBar(
    elevated: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevationPx = with(LocalDensity.current) { TopBarElevation.toPx() }
    val shadowElevation = remember { Animatable(0f) }

    LaunchedEffect(elevated, elevationPx) {
        shadowElevation.animateTo(if (elevated) elevationPx else 0f)
    }

    TopAppBar(
        title = { MosaicWordmark() },
        actions = {
            AppBarAction(
                icon = R.drawable.ic_settings,
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.nav_settings),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.graphicsLayer { this.shadowElevation = shadowElevation.value },
    )
}

private val TopBarElevation = 4.dp

@Composable
private fun FeedList(
    posts: List<PostCardData>,
    endReached: Boolean,
    loadMoreFailed: Boolean,
    autoPlayVideos: Boolean,
    listState: LazyListState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalVideoPlayback.current
    // Read the latest posts inside the collector without keying the effect on them: a like or
    // bookmark toggle replaces the list instance but never changes which posts carry a video or
    // where they sit, so restarting the autoplay collector on every toggle is wasted work. A
    // change that actually matters (scroll, refresh) re-lays out the list and re-emits layoutInfo.
    val currentPosts by rememberUpdatedState(posts)

    // autoPlayVideos *is* a key: it changes only when the user flips the setting, and restarting
    // the collector re-reads the current layout, so turning auto-play on plays the video already
    // on screen instead of waiting for the next scroll.
    LaunchedEffect(listState, playback, autoPlayVideos) {
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            if (playback == null || playback.isFullscreen) return@collect
            // With auto-play off and nothing playing there is neither a video to start nor one to
            // stop, so the per-frame visibility scan below would be pure waste.
            if (!autoPlayVideos && playback.activeVideoUrl == null) return@collect

            val posts = currentPosts
            // FeedList emits the posts first, so a list index is a post index.
            val mostVisible = mostVisibleVideoIndex(
                visibleItems = layoutInfo.visibleItemsInfo,
                viewportStart = layoutInfo.viewportStartOffset,
                viewportEnd = layoutInfo.viewportEndOffset,
                hasVideo = { it < posts.size && posts[it].post.video != null },
            )
            val activeUrl = playback.activeVideoUrl
            val url = videoToPlay(
                mostVisibleUrl = mostVisible?.let { posts[it].post.video?.videoUrl },
                activeUrl = activeUrl,
                autoPlayVideos = autoPlayVideos,
            )

            // This runs on every scroll frame; the controller only needs to hear about a change.
            when {
                url == activeUrl -> Unit
                url != null -> playback.playInline(url)
                else -> playback.stopPlayback()
            }
        }
    }

    LoadMoreEffect(
        listState = listState,
        endReached = endReached,
        loadMoreFailed = loadMoreFailed,
        onLoadMore = { onEvent(UiEvent.LoadMore) },
    )

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(
            items = posts,
            key = { it.post.id },
            contentType = { it.post.cardContentType },
        ) { data ->
            // The lambdas capture the ids, never `data`: every emission rebuilds each
            // PostCardData, and a lambda that captured one would make every row recompose.
            val postId = data.post.id
            val authorId = data.author.id

            PostCard(
                post = data.post,
                author = data.author,
                onToggleLike = { onEvent(UiEvent.ToggleLike(postId)) },
                onToggleBookmark = { onEvent(UiEvent.ToggleBookmark(postId)) },
                onOpenProfile = { onEvent(UiEvent.OpenProfile(authorId)) },
                onOpenVideo = { video -> onEvent(UiEvent.OpenVideo(video)) },
                onOpenAlbum = { urls, index ->
                    onEvent(UiEvent.OpenAlbum(urls, index))
                },
                onOpenPost = { onEvent(UiEvent.OpenPost(postId)) },
                onReport = { onEvent(UiEvent.OpenReport(postId)) },
                onDelete = if (data.isOwn) ({ onEvent(UiEvent.Delete(postId)) }) else null,
            )
        }
        if (endReached) {
            item(key = "caught_up") {
                CaughtUpFooter()
            }
        } else {
            item(key = "loading_more") {
                LoadMoreFooter(failed = loadMoreFailed, onRetry = { onEvent(UiEvent.LoadMore) })
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CaughtUpFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.feed_caught_up),
        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.02.em),
        color = LocalMosaicColors.current.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

/**
 * With auto-play off, only a video the user started may keep playing, and only while it is still
 * the most visible.
 */
internal fun videoToPlay(
    mostVisibleUrl: String?,
    activeUrl: String?,
    autoPlayVideos: Boolean,
): String? = mostVisibleUrl?.takeIf { autoPlayVideos || it == activeUrl }

/**
 * The index of the most visible item holding a video, or null when that item shows less than half
 * of itself. Inline and index-based because it runs on every scroll frame.
 */
internal inline fun mostVisibleVideoIndex(
    visibleItems: List<LazyListItemInfo>,
    viewportStart: Int,
    viewportEnd: Int,
    hasVideo: (index: Int) -> Boolean,
): Int? {
    var winner = -1
    var winnerFraction = 0f

    for (i in visibleItems.indices) {
        val item = visibleItems[i]
        if (item.size <= 0 || !hasVideo(item.index)) continue

        val visible = minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, viewportStart)
        val fraction = maxOf(0, visible).toFloat() / item.size
        if (winner == -1 || fraction > winnerFraction) {
            winner = item.index
            winnerFraction = fraction
        }
    }

    return winner.takeIf { it != -1 && winnerFraction >= 0.5f }
}

@Preview(showBackground = true)
@Composable
private fun HomeFeedPreview() {
    val users = SampleUsers.associateBy { it.id }
    val feed = SamplePosts.mapNotNull { post ->
        PostCardData(post, users[post.authorId] ?: return@mapNotNull null)
    }

    MosaicTheme {
        HomeScreen(
            uiState = UiState.Feed(feed, endReached = true),
            isRefreshing = false,
            autoPlayVideos = true,
            failedAction = null,
            report = null,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    MosaicTheme {
        HomeScreen(
            uiState = UiState.Feed(posts = emptyList(), endReached = true),
            isRefreshing = false,
            autoPlayVideos = true,
            failedAction = null,
            report = null,
            onEvent = {},
        )
    }
}
