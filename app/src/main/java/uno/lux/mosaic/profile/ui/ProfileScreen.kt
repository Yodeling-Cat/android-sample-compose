package uno.lux.mosaic.profile.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.FailedActionEffect
import uno.lux.mosaic.common.ui.FullScreenError
import uno.lux.mosaic.common.ui.FullScreenProgress
import uno.lux.mosaic.common.ui.LoadMoreEffect
import uno.lux.mosaic.common.ui.LoadMoreFooter
import uno.lux.mosaic.common.ui.asText
import uno.lux.mosaic.designsystem.components.ScrimIconButton
import uno.lux.mosaic.designsystem.components.rememberDebounced
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicElevations
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.domain.Post
import uno.lux.mosaic.post.ui.PostCard
import uno.lux.mosaic.post.ui.cardContentType
import uno.lux.mosaic.post.ui.report.PostReport
import uno.lux.mosaic.post.ui.report.ReportHost
import uno.lux.mosaic.profile.domain.Profile
import uno.lux.mosaic.user.domain.User
import uno.lux.mosaic.user.domain.UserId
import uno.lux.mosaic.common.R as CommonR
import uno.lux.mosaic.profile.ui.ProfileUiEvent as UiEvent
import uno.lux.mosaic.profile.ui.ProfileUiState as UiState

@Composable
fun ProfileScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    viewModel: ProfileViewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
        creationCallback = { factory -> factory.create(userId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val failedAction by viewModel.failedAction.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        failedAction = failedAction,
        report = report,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        showBackButton = showBackButton,
    )
}

@Composable
internal fun ProfileScreen(
    uiState: UiState,
    isRefreshing: Boolean,
    failedAction: FailedAction?,
    report: PostReport?,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val onBack: (() -> Unit)? = if (showBackButton) ({ onEvent(UiEvent.GoBack) }) else null

    FailedActionEffect(failedAction, snackbarHostState) { onEvent(UiEvent.FailedActionShown) }

    ReportHost(
        report = report,
        snackbarHostState = snackbarHostState,
        onEvent = { onEvent(UiEvent.ReportDialog(it)) },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (uiState) {
            UiState.Loading -> {
                FullScreenProgress()
                PlainBackButton(onBack)
            }

            is UiState.Error -> {
                FullScreenError(
                    message = uiState.error.asText(),
                    onRetry = { onEvent(UiEvent.Retry) },
                )
                PlainBackButton(onBack)
            }

            UiState.NotFound -> {
                CenteredMessage(stringResource(R.string.profile_not_found))
                PlainBackButton(onBack)
            }

            // Draws its own TopAppBar over the cover, so it needs no PlainBackButton.
            is UiState.Loaded -> {
                ProfileContent(
                    data = uiState.data,
                    isCurrentUser = uiState.isCurrentUser,
                    isRefreshing = isRefreshing,
                    onEvent = onEvent,
                    onBack = onBack,
                )
            }
        }

        // No Scaffold to host it, so the snackbar overlays the Box instead.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    data: ProfileScreenData,
    isCurrentUser: Boolean,
    isRefreshing: Boolean,
    onEvent: (UiEvent) -> Unit,
    onBack: (() -> Unit)?,
) {
    val tabs = remember(isCurrentUser) {
        ProfileTab.entries.filter { isCurrentUser || !it.ownerOnly }
    }
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    val listState = rememberLazyListState()
    // The cover and identity block collapse behind the bar; the tab row rides up with them until
    // it pins flush beneath it.
    val header = rememberCollapsingHeaderState()
    val barBottomPx = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this) + ProfileBarHeight.roundToPx()
    }

    ProfileTabEffects(
        selectedTab = selectedTab,
        data = data,
        listState = listState,
        onEvent = onEvent,
    )

    // Pull-to-refresh wraps the collapse Box so its connection sits *outside* the collapse one: a
    // downward drag re-expands the header before the refresh gesture gets the leftover.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onEvent(UiEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(header.nestedScrollConnection),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileHeader(
                    user = data.user,
                    isCurrentUser = isCurrentUser,
                    onEditProfile = { onEvent(UiEvent.OpenEditProfile) },
                    onToggleFollow = { onEvent(UiEvent.ToggleFollow) },
                    onOpenAvatar = { url -> onEvent(UiEvent.OpenAvatar(url)) },
                    modifier = Modifier.collapsingHeader(header, pinnedAtPx = barBottomPx),
                )
                ProfileTabs(
                    tabs = tabs,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    tabItems(
                        selectedTab = selectedTab,
                        data = data,
                        isCurrentUser = isCurrentUser,
                        onEvent = onEvent,
                    )
                    item(key = "bottom-inset") {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }

            ProfileTopBar(
                userName = data.user.nickname,
                header = header,
                onBack = onBack,
            )
        }
    }
}

/** Every tab pages the one list, so only the showing tab's effects run. */
@Composable
private fun ProfileTabEffects(
    selectedTab: ProfileTab,
    data: ProfileScreenData,
    listState: LazyListState,
    onEvent: (UiEvent) -> Unit,
) {
    val onDemand = data.onDemandTab(selectedTab)

    if (onDemand == null) {
        LoadMoreEffect(
            listState = listState,
            endReached = data.postsEndReached,
            loadMoreFailed = data.postsLoadMoreFailed,
            onLoadMore = { onEvent(UiEvent.LoadMorePosts) },
        )
    } else {
        // Keyed so a switch straight from one on-demand tab to the other restarts its effects.
        key(selectedTab) {
            OnDemandTabEffects(listState = listState, tab = onDemand, onEvent = onEvent)
        }
    }
}

private fun LazyListScope.tabItems(
    selectedTab: ProfileTab,
    data: ProfileScreenData,
    isCurrentUser: Boolean,
    onEvent: (UiEvent) -> Unit,
) {
    val onDemand = data.onDemandTab(selectedTab)

    if (onDemand == null) {
        postItems(
            screenData = data,
            onEvent = onEvent,
            isCurrentUser = isCurrentUser,
        )
    } else {
        onDemandTabItems(tab = onDemand, onEvent = onEvent)
    }
}

private val ProfileBarHeight = 64.dp // Material small TopAppBar content height (excl. status bar)

private enum class ProfileTab(
    @get:StringRes val labelRes: Int,
    val ownerOnly: Boolean = false,
) {
    POSTS(R.string.profile_tab_posts),
    LIKES(R.string.profile_tab_likes),
    BOOKMARKS(R.string.profile_tab_saved, true),
}

@Immutable
private data class OnDemandTab(
    val list: ProfilePostList?,
    val loadFailed: Boolean,
    val shown: UiEvent,
    val loadMore: UiEvent,
    val keyPrefix: String,
    @get:StringRes val emptyMessageRes: Int,
)

/** Null for [ProfileTab.POSTS], which loads with the profile rather than on first open. */
private fun ProfileScreenData.onDemandTab(tab: ProfileTab): OnDemandTab? =
    when (tab) {
        ProfileTab.POSTS -> null

        ProfileTab.LIKES -> OnDemandTab(
            list = likes,
            loadFailed = likesLoadFailed,
            shown = UiEvent.LikesTabShown,
            loadMore = UiEvent.LoadMoreLikes,
            keyPrefix = "likes",
            emptyMessageRes = R.string.profile_empty_likes,
        )

        ProfileTab.BOOKMARKS -> OnDemandTab(
            list = bookmarks,
            loadFailed = bookmarksLoadFailed,
            shown = UiEvent.BookmarksTabShown,
            loadMore = UiEvent.LoadMoreBookmarks,
            keyPrefix = "bookmarks",
            emptyMessageRes = R.string.profile_empty_saved,
        )
    }

@Composable
private fun ProfileTabs(
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
) {
    // Opaque so it hides the posts scrolling under it once pinned, and sits seamlessly under the
    // bar it ends up flush beneath (see CollapsingHeaderState).
    PrimaryTabRow(
        // The index into the *visible* tabs, which ownerOnly makes narrower than the enum — so
        // this is not the selected tab's ordinal.
        selectedTabIndex = tabs.indexOf(selected),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = LocalMosaicColors.current.textTertiary,
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

private fun LazyListScope.postItems(
    screenData: ProfileScreenData,
    onEvent: (UiEvent) -> Unit,
    isCurrentUser: Boolean,
) {
    val posts = screenData.posts
    val author = screenData.user

    if (posts.isEmpty()) {
        item(key = "posts-empty") { EmptyTab(R.string.profile_empty_posts) }
        return
    }
    items(
        items = posts,
        key = { it.id },
        contentType = { it.cardContentType },
    ) { post ->
        ProfilePostCard(
            post = post,
            author = author,
            // Every post here is by the profile's user, so "own post" is whose profile this is.
            isOwn = isCurrentUser,
            // Already on this author's profile — tapping the header again is a no-op.
            onOpenProfile = {},
            onEvent = onEvent,
        )
    }
    if (!screenData.postsEndReached) {
        item(key = "posts-loading-more") {
            LoadMoreFooter(
                failed = screenData.postsLoadMoreFailed,
                onRetry = { onEvent(UiEvent.LoadMorePosts) },
            )
        }
    }
}

@Composable
private fun OnDemandTabEffects(
    listState: LazyListState,
    tab: OnDemandTab,
    onEvent: (UiEvent) -> Unit,
) {
    LaunchedEffect(Unit) { onEvent(tab.shown) }

    LoadMoreEffect(
        listState = listState,
        endReached = tab.list?.endReached ?: true,
        loadMoreFailed = tab.loadFailed,
        onLoadMore = { onEvent(tab.loadMore) },
    )
}

private fun LazyListScope.onDemandTabItems(tab: OnDemandTab, onEvent: (UiEvent) -> Unit) {
    val list = tab.list
    val keyPrefix = tab.keyPrefix

    if (list == null) {
        item(key = "$keyPrefix-loading") {
            LoadMoreFooter(failed = tab.loadFailed, onRetry = { onEvent(tab.shown) })
        }
        return
    }

    if (list.posts.isEmpty()) {
        item(key = "$keyPrefix-empty") { EmptyTab(tab.emptyMessageRes) }
        return
    }

    items(
        items = list.posts,
        key = { "$keyPrefix-${it.post.id}" },
        contentType = { it.post.cardContentType },
    ) { data ->
        // The lambda captures the id, never `data`: every emission rebuilds each PostCardData,
        // and a lambda that captured one would make every row recompose.
        val authorId = data.author.id

        ProfilePostCard(
            post = data.post,
            author = data.author,
            isOwn = data.isOwn,
            // A saved or liked post can be by anyone, so its header opens that author's profile.
            onOpenProfile = { onEvent(UiEvent.OpenProfile(authorId)) },
            onEvent = onEvent,
        )
    }

    if (!list.endReached) {
        item(key = "$keyPrefix-loading-more") {
            LoadMoreFooter(failed = tab.loadFailed, onRetry = { onEvent(tab.loadMore) })
        }
    }
}

@Composable
private fun ProfilePostCard(
    post: Post,
    author: User,
    isOwn: Boolean,
    onOpenProfile: () -> Unit,
    onEvent: (UiEvent) -> Unit,
) {
    val postId = post.id

    PostCard(
        post = post,
        author = author,
        onToggleLike = { onEvent(UiEvent.ToggleLike(postId)) },
        onToggleBookmark = { onEvent(UiEvent.ToggleBookmark(postId)) },
        onOpenProfile = onOpenProfile,
        onOpenVideo = { video -> onEvent(UiEvent.OpenVideo(video)) },
        onOpenAlbum = { urls, index -> onEvent(UiEvent.OpenAlbum(urls, index)) },
        onOpenPost = { onEvent(UiEvent.OpenPost(postId)) },
        onReport = { onEvent(UiEvent.OpenReport(postId)) },
        onDelete = if (isOwn) ({ onEvent(UiEvent.Delete(postId)) }) else null,
    )
}

@Composable
private fun EmptyTab(
    @StringRes messageRes: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalMosaicColors.current.textTertiary,
        )
    }
}

/** Draws nothing when [onBack] is null, which is a profile that is a tab rather than a pushed page. */
@Composable
private fun BoxScope.PlainBackButton(onBack: (() -> Unit)?) {
    if (onBack == null) return

    IconButton(
        onClick = onBack.rememberDebounced(),
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 4.dp, top = 4.dp),
    ) {
        Icon(
            painter = painterResource(CommonR.drawable.ic_arrow_back),
            contentDescription = stringResource(CommonR.string.navigate_back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val BAR_FILL_FRACTION = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    userName: String,
    header: CollapsingHeaderState,
    onBack: (() -> Unit)?,
) {
    val collapsed = header.collapsedFraction
    val progress = (collapsed / BAR_FILL_FRACTION).coerceIn(0f, 1f)
    // `collapsed` reaches exactly 1f at full collapse, so this is the true pinned state.
    val pinned by animateFloatAsState(if (collapsed >= 1f) 1f else 0f, label = "appBarPinned")

    TopAppBar(
        modifier = Modifier.shadow(
            elevation = MosaicElevations.ScrolledBar * progress * (1f - pinned),
        ),
        title = {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = progress),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                ScrimIconButton(
                    iconRes = CommonR.drawable.ic_arrow_back,
                    contentDescription = stringResource(CommonR.string.navigate_back),
                    progress = progress,
                    onClick = onBack,
                )
            }
        },
        actions = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = progress),
        ),
    )
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun sampleProfileData(): ProfileScreenData {
    val user = SampleUsers.first()
    val posts = SamplePosts.filter { it.authorId == user.id }

    return ProfileScreenData(
        user = user,
        profile = Profile(userId = user.id, postsCount = posts.size),
        posts = posts,
    )
}

@Preview(showBackground = true, name = "My user")
@Composable
private fun ProfileScreenPreview() {
    MosaicTheme {
        ProfileScreen(
            uiState = UiState.Loaded(sampleProfileData(), isCurrentUser = true),
            isRefreshing = false,
            failedAction = null,
            report = null,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Another user")
@Composable
private fun ProfileScreenOtherUserPreview() {
    MosaicTheme {
        ProfileScreen(
            uiState = UiState.Loaded(sampleProfileData(), isCurrentUser = false),
            isRefreshing = false,
            failedAction = null,
            report = null,
            onEvent = {},
        )
    }
}
