package uno.lux.mosaic.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.video.data.domain.Video
import uno.lux.mosaic.video.ui.LocalVideoPlayback
import uno.lux.mosaic.common.R as CommonR

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsStateWithLifecycle()
    val failedAction by viewModel.failedAction.collectAsStateWithLifecycle()
    val reportSend by viewModel.reportSend.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        autoPlayVideos = autoPlayVideos,
        failedAction = failedAction,
        reportSend = reportSend,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * Stateless feed screen — renders [uiState] and reports every interaction as a [HomeUiEvent]
 * through [onEvent]. Holding no ViewModel makes it directly previewable and testable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    isRefreshing: Boolean,
    autoPlayVideos: Boolean,
    failedAction: FailedAction?,
    reportSend: ReportSendState,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // The bar is pinned; its shadow fades in whenever the list content is scrolled and clears at
    // the very top. canScrollBackward is already a coalesced boolean snapshot — it only changes
    // when crossing the top — so reading it directly needs no derivedStateOf wrapper.
    val snackbarHostState = remember { SnackbarHostState() }
    // A refresh that failed over a feed already on screen — announced without taking the posts
    // away. The duration is stated because Material's default is Indefinite whenever an action
    // label is given: right for a decision the user must make, wrong for a feed that is still
    // perfectly readable behind it.
    val refreshErrorMessage = (uiState as? HomeUiState.Feed)?.refreshError?.asText()
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
        onEvent(HomeUiEvent.RefreshErrorShown)
        if (result == SnackbarResult.ActionPerformed) onEvent(HomeUiEvent.Refresh)
    }

    // A delete whose request failed after its dialog already closed.
    FailedActionEffect(failedAction, snackbarHostState) { onEvent(HomeUiEvent.FailedActionShown) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeedTopBar(
                elevated = true,
                onOpenSettings = { onEvent(HomeUiEvent.OpenSettings) },
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { onEvent(HomeUiEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (uiState) {
                HomeUiState.Loading -> {
                    FullScreenProgress()
                }

                is HomeUiState.Error -> {
                    FullScreenError(
                        message = uiState.error.asText(),
                        onRetry = { onEvent(HomeUiEvent.Retry) },
                    )
                }

                is HomeUiState.Feed -> {
                    if (uiState.posts.isEmpty()) {
                        EmptyState()
                    } else {
                        FeedList(
                            posts = uiState.posts,
                            endReached = uiState.endReached,
                            loadMoreFailed = uiState.loadMoreFailed,
                            autoPlayVideos = autoPlayVideos,
                            reportSend = reportSend,
                            listState = listState,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The feed's pinned top bar — it stays in place while the feed scrolls and fades in a drop shadow
 * whenever the list content is scrolled away from the top ([elevated]), so the shadow is a stable
 * indicator of scroll position rather than something that rides the bar.
 */
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

/** Resting shadow depth of the pinned feed bar once the list is scrolled. */
private val TopBarElevation = 4.dp

@Composable
private fun FeedList(
    posts: List<PostCardData>,
    endReached: Boolean,
    loadMoreFailed: Boolean,
    autoPlayVideos: Boolean,
    reportSend: ReportSendState,
    listState: LazyListState,
    onEvent: (HomeUiEvent) -> Unit,
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

            val url = videoToPlay(
                mostVisibleUrl = mostVisibleVideo(layoutInfo, currentPosts)?.videoUrl,
                activeUrl = playback.activeVideoUrl,
                autoPlayVideos = autoPlayVideos,
            )
            if (url != null) playback.playInline(url) else playback.stopPlayback()
        }
    }

    LoadMoreEffect(
        listState = listState,
        endReached = endReached,
        loadMoreFailed = loadMoreFailed,
        onLoadMore = { onEvent(HomeUiEvent.LoadMore) },
    )

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(posts, key = { it.post.id }) { data ->
            PostCard(
                post = data.post,
                author = data.author,
                reportSend = reportSend,
                onToggleLike = { onEvent(HomeUiEvent.ToggleLike(data.post.id)) },
                onToggleBookmark = { onEvent(HomeUiEvent.ToggleBookmark(data.post.id)) },
                onOpenProfile = { onEvent(HomeUiEvent.OpenProfile(data.author.id)) },
                onOpenVideo = { video -> onEvent(HomeUiEvent.OpenVideo(video)) },
                onOpenAlbum = { urls, index ->
                    onEvent(HomeUiEvent.OpenAlbum(urls, index))
                },
                onOpenPost = { onEvent(HomeUiEvent.OpenPost(data.post.id)) },
                onReport = { reason, details ->
                    onEvent(HomeUiEvent.Report(data.post.id, reason, details))
                },
                onReportClosed = { onEvent(HomeUiEvent.CloseReport) },
                onDelete = if (data.isOwn) ({ onEvent(HomeUiEvent.Delete(data.post.id)) }) else null,
            )
        }
        if (endReached) {
            item(key = "caught_up") {
                CaughtUpFooter()
            }
        } else {
            item(key = "loading_more") {
                LoadMoreFooter(failed = loadMoreFailed, onRetry = { onEvent(HomeUiEvent.LoadMore) })
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

/** End-of-feed marker shown as the last list item once there are no more posts to load. */
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
 * The URL the feed should have playing, or null to stop.
 *
 * With auto-play on that is simply the on-screen winner ([mostVisibleUrl]). With it off nothing
 * ever starts on its own, so the only URL that may play is one already playing ([activeUrl]) —
 * i.e. one the user tapped — and only while it is still the winner, which is what stops it when it
 * scrolls out of the way rather than letting its audio outlive the post on screen.
 *
 * Pure, so the whole rule is unit-testable without a composition or a layout.
 */
internal fun videoToPlay(
    mostVisibleUrl: String?,
    activeUrl: String?,
    autoPlayVideos: Boolean,
): String? = mostVisibleUrl?.takeIf { autoPlayVideos || it == activeUrl }

/**
 * Returns the on-screen winner — the video post with the greatest visible fraction that meets the
 * 50 % threshold — or null when no video qualifies. Deciding what to do with it is [videoToPlay]'s.
 *
 * [LazyListItemInfo.index] maps 1:1 to [posts] because [FeedList] emits posts first (indices
 * 0..lastIndex) then optionally a footer item at [posts.size].
 */
private fun mostVisibleVideo(layoutInfo: LazyListLayoutInfo, posts: List<PostCardData>): Video? {
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset

    fun visibleFraction(offset: Int, size: Int): Float {
        val visibleTop = maxOf(offset, viewportStart)
        val visibleBottom = minOf(offset + size, viewportEnd)
        return maxOf(0, visibleBottom - visibleTop).toFloat() / size
    }

    val (winner, fraction) = layoutInfo.visibleItemsInfo
        .filter { it.index < posts.size && posts[it.index].post.video != null }
        .map { it to visibleFraction(it.offset, it.size) }
        .maxByOrNull { (_, f) -> f }
        ?: return null

    return posts[winner.index].post.video.takeIf { fraction >= 0.5f }
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
            uiState = HomeUiState.Feed(feed, endReached = true),
            isRefreshing = false,
            autoPlayVideos = true,
            failedAction = null,
            reportSend = ReportSendState.IDLE,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    MosaicTheme {
        HomeScreen(
            uiState = HomeUiState.Feed(posts = emptyList(), endReached = true),
            isRefreshing = false,
            autoPlayVideos = true,
            failedAction = null,
            reportSend = ReportSendState.IDLE,
            onEvent = {},
        )
    }
}
