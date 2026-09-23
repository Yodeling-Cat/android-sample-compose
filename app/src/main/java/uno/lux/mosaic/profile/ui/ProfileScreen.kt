package uno.lux.mosaic.profile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
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
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.FailedActionEffect
import uno.lux.mosaic.common.ui.FullScreenError
import uno.lux.mosaic.common.ui.FullScreenProgress
import uno.lux.mosaic.common.ui.LoadMoreEffect
import uno.lux.mosaic.common.ui.LoadMoreFooter
import uno.lux.mosaic.common.util.compactCount
import uno.lux.mosaic.designsystem.components.ScrimIconButton
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.components.rememberDebounced
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicElevations
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.ui.PostCard
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.profile.data.domain.Profile
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.ui.Avatar
import kotlin.math.roundToInt
import uno.lux.mosaic.common.R as CommonR
import uno.lux.mosaic.profile.ui.ProfileUiEvent as UiEvent
import uno.lux.mosaic.profile.ui.ProfileUiState as UiState

@Composable
fun ProfileScreen(
    userId: UserId,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    // The ViewModel store is per back-stack entry, so each opened profile gets its own ViewModel.
    viewModel: ProfileViewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
        creationCallback = { factory -> factory.create(userId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val failedAction by viewModel.failedAction.collectAsStateWithLifecycle()
    val reportSend by viewModel.reportSend.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        failedAction = failedAction,
        reportSend = reportSend,
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
    reportSend: ReportSendState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val onBack: (() -> Unit)? = if (showBackButton) ({ onEvent(UiEvent.GoBack) }) else null

    FailedActionEffect(failedAction, snackbarHostState) { onEvent(UiEvent.FailedActionShown) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (uiState) {
            UiState.Loading -> {
                FullScreenProgress()
                if (onBack != null) PlainBackButton(onBack, Modifier.align(Alignment.TopStart))
            }

            is UiState.Error -> {
                FullScreenError(
                    message = uiState.error.asText(),
                    onRetry = { onEvent(UiEvent.Retry) },
                )
                if (onBack != null) PlainBackButton(onBack, Modifier.align(Alignment.TopStart))
            }

            UiState.NotFound -> {
                CenteredMessage(stringResource(R.string.profile_not_found))
                if (onBack != null) PlainBackButton(onBack, Modifier.align(Alignment.TopStart))
            }

            // Draws its own TopAppBar over the cover, so it needs no PlainBackButton.
            is UiState.Loaded -> {
                ProfileContent(
                    data = uiState.data,
                    isCurrentUser = uiState.isCurrentUser,
                    isRefreshing = isRefreshing,
                    reportSend = reportSend,
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

/**
 * Saves the header's collapse offset by hand: `rememberSaveable { mutableFloatStateOf(…) }`
 * resolves to the `MutableState<Float>` overload, giving up the [FloatState] that the layout
 * pass and the app bar read on every frame.
 */
private val CollapseSaver = Saver<MutableFloatState, Float>(
    save = { it.floatValue },
    restore = { mutableFloatStateOf(it) },
)

// TODO: Could use refactoring, breaking up into smaller functions
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    data: ProfileScreenData,
    isCurrentUser: Boolean,
    isRefreshing: Boolean,
    reportSend: ReportSendState,
    onEvent: (UiEvent) -> Unit,
    onBack: (() -> Unit)?,
) {
    // Saved is the signed-in user's own private list, so it is hidden on anyone else's profile.
    val tabs = remember(isCurrentUser) {
        ProfileTab.entries.filter { isCurrentUser || !it.ownerOnly }
    }
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    val listState = rememberLazyListState()

    // A collapsing header: the cover + identity block slides up behind the app bar as you scroll,
    // and the tab row rides up with it until it pins flush beneath the bar. The two motions are
    // one gesture because both read the same `collapse` offset, advanced by the connection below.
    val density = LocalDensity.current
    val barBottomPx = with(density) {
        WindowInsets.statusBars.getTop(this) + ProfileBarHeight.roundToPx()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Saveable, unlike the measured height above: the collapse *is* the top of this page's scroll
    // position, and `rememberLazyListState` only remembers the part below it.
    val collapse = rememberSaveable(saver = CollapseSaver) { mutableFloatStateOf(0f) }
    // The header collapses until its foot reaches the bar; past that the tabs are pinned and the
    // list takes over. Keyed off the measured height, never off `collapse`, so a scroll relayouts
    // the header and recomposes the bar, but not this scope.
    val maxCollapse = (headerHeightPx - barBottomPx).coerceAtLeast(0).toFloat()

    val collapseConnection = remember(maxCollapse) {
        object : NestedScrollConnection {
            // Fold `dy` into the collapse offset, returning what it consumed (same sign).
            fun consume(dy: Float): Offset {
                val before = collapse.floatValue
                collapse.floatValue = (before - dy).coerceIn(0f, maxCollapse)
                return Offset(x = 0f, y = before - collapse.floatValue)
            }

            // Scrolling up collapses the header before the posts list scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y >= 0f) Offset.Zero else consume(available.y)

            // Scrolling down re-expands the header from the leftover once the list is at its top
            // — and, since pull-to-refresh wraps this, before the refresh sees it.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                if (available.y <= 0f) Offset.Zero else consume(available.y)
        }
    }

    // Every tab pages the one posts list, so the effects follow whichever is showing.
    when (selectedTab) {
        ProfileTab.POSTS -> LoadMoreEffect(
            listState = listState,
            endReached = data.postsEndReached,
            loadMoreFailed = data.postsLoadMoreFailed,
            onLoadMore = { onEvent(UiEvent.LoadMorePosts) },
        )

        ProfileTab.LIKES -> OnDemandTabEffects(
            listState = listState,
            list = data.likes,
            loadFailed = data.likesLoadFailed,
            onShown = { onEvent(UiEvent.LikesTabShown) },
            onLoadMore = { onEvent(UiEvent.LoadMoreLikes) },
        )

        ProfileTab.SAVED -> OnDemandTabEffects(
            listState = listState,
            list = data.bookmarks,
            loadFailed = data.bookmarksLoadFailed,
            onShown = { onEvent(UiEvent.SavedTabShown) },
            onLoadMore = { onEvent(UiEvent.LoadMoreBookmarks) },
        )
    }

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
                .nestedScroll(collapseConnection),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileHeader(
                    user = data.user,
                    isCurrentUser = isCurrentUser,
                    onEditProfile = { onEvent(UiEvent.OpenEditProfile) },
                    onToggleFollow = { onEvent(UiEvent.ToggleFollow) },
                    onOpenAvatar = { url -> onEvent(UiEvent.OpenAvatar(url)) },
                    // Reserve `collapse` less height and draw the header shifted up by that much,
                    // so it slides behind the bar with no gap left below. Read in the layout pass,
                    // so a scroll reflows the header without recomposing this screen.
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        if (placeable.height != headerHeightPx) headerHeightPx = placeable.height

                        val offset = collapse.floatValue.roundToInt()
                        layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                            placeable.place(0, -offset)
                        }
                    },
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
                    when (selectedTab) {
                        ProfileTab.POSTS -> postItems(
                            screenData = data,
                            reportSend = reportSend,
                            onEvent = onEvent,
                            isCurrentUser = isCurrentUser,
                        )

                        ProfileTab.LIKES -> onDemandTabItems(
                            list = data.likes,
                            loadFailed = data.likesLoadFailed,
                            onFirstLoad = { onEvent(UiEvent.LikesTabShown) },
                            onLoadMore = { onEvent(UiEvent.LoadMoreLikes) },
                            reportSend = reportSend,
                            onEvent = onEvent,
                            keyPrefix = "likes",
                            emptyMessageRes = R.string.profile_empty_likes,
                        )

                        ProfileTab.SAVED -> onDemandTabItems(
                            list = data.bookmarks,
                            loadFailed = data.bookmarksLoadFailed,
                            onFirstLoad = { onEvent(UiEvent.SavedTabShown) },
                            onLoadMore = { onEvent(UiEvent.LoadMoreBookmarks) },
                            reportSend = reportSend,
                            onEvent = onEvent,
                            keyPrefix = "saved",
                            emptyMessageRes = R.string.profile_empty_saved,
                        )
                    }
                    item(key = "bottom-inset") {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }

            ProfileTopBar(
                userName = data.user.nickname,
                collapse = collapse,
                maxCollapse = maxCollapse,
                onBack = onBack,
            )
        }
    }
}

private val CoverHeight = 160.dp
private val AvatarRingSize = 96.dp
private val AvatarSize = 88.dp
private val AvatarOverlap = 44.dp // how far the avatar hangs below the cover
private val ProfileBarHeight = 64.dp // Material small TopAppBar content height (excl. status bar)

@Composable
private fun ProfileHeader(
    user: User,
    isCurrentUser: Boolean,
    onEditProfile: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (avatarUrl: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Tall enough to contain the hanging avatar, which is the last child so it wins the
        // z-order and nothing below paints over it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CoverHeight + (AvatarRingSize - AvatarOverlap)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CoverHeight)
                    .background(MosaicGradients.mediaBrush(user.id)),
            )
            // Primary action, bottom-right across from the avatar: Edit profile on your own
            // profile, Follow/Unfollow on anyone else's.
            val actionModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
            if (isCurrentUser) {
                FilledTonalButton(
                    onClick = onEditProfile.rememberDebounced(),
                    modifier = actionModifier,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_edit))
                }
            } else {
                FollowButton(
                    isFollowing = user.isFollowing,
                    onToggleFollow = onToggleFollow,
                    modifier = actionModifier,
                )
            }
            AvatarRing(
                user = user,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .padding(top = CoverHeight - AvatarOverlap)
                    .clip(CircleShape)
                    .debouncedClickable(enabled = !user.avatarUrl.isNullOrEmpty(), onClick = {
                        if (user.avatarUrl != null) onOpenAvatar(user.avatarUrl)
                    }),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = user.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = user.handle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMosaicColors.current.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            IdentityChips(user)
            user.bio?.let { bio ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            StatsRow(user)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A circular avatar wrapped in a surface-colored ring, so it reads against the cover. */
@Composable
private fun AvatarRing(
    user: User,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AvatarRingSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(user = user, size = AvatarSize)
    }
}

/** Filled while not yet following, tonal once following — both solid to read over the cover. */
@Composable
private fun FollowButton(
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick = onToggleFollow.rememberDebounced()

    if (isFollowing) {
        FilledTonalButton(onClick = onClick, modifier = modifier) {
            Text(stringResource(R.string.profile_following))
        }
    } else {
        Button(onClick = onClick, modifier = modifier) {
            Text(stringResource(R.string.profile_follow))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityChips(user: User) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        user.age?.let { age -> ProfileChip(text = age.toString()) }
        user.gender?.let { gender -> ProfileChip(text = gender) }
        user.location?.let { location ->
            ProfileChip(text = location, leadingIcon = R.drawable.ic_place)
        }
    }
}

@Composable
private fun ProfileChip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = LocalMosaicColors.current.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsRow(user: User, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Stat(value = user.followerCount, label = stringResource(R.string.profile_stat_followers))
        Stat(value = user.followingCount, label = stringResource(R.string.profile_stat_following))
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = compactCount(value).asText(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalMosaicColors.current.textTertiary,
        )
    }
}

/**
 * The profile's tab entries, in order. Adding a tab is an enum addition plus its branch in
 * [ProfileContent]. [ownerOnly] marks a tab that belongs only on the signed-in user's own
 * profile: Saved is private, and the server refuses it to anyone else. Likes are public.
 */
private enum class ProfileTab(
    @get:StringRes val labelRes: Int,
    val ownerOnly: Boolean = false,
) {
    POSTS(R.string.profile_tab_posts),
    LIKES(R.string.profile_tab_likes),
    SAVED(R.string.profile_tab_saved, true),
}

@Composable
private fun ProfileTabs(
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
) {
    // Opaque so it hides the posts scrolling under it once pinned, and sits seamlessly under the
    // bar it ends up flush beneath (see maxCollapse in ProfileContent).
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
    reportSend: ReportSendState,
    onEvent: (UiEvent) -> Unit,
    isCurrentUser: Boolean,
) {
    val posts = screenData.posts
    val author = screenData.user

    if (posts.isEmpty()) {
        item(key = "posts-empty") { EmptyTab(R.string.profile_empty_posts) }
        return
    }
    items(posts, key = { it.id }) { post ->
        PostCard(
            post = post,
            author = author,
            reportSend = reportSend,
            onToggleLike = { onEvent(UiEvent.ToggleLike(post.id)) },
            onToggleBookmark = { onEvent(UiEvent.ToggleBookmark(post.id)) },
            // Already on this author's profile — tapping the header again is a no-op.
            onOpenProfile = {},
            onOpenVideo = { video -> onEvent(UiEvent.OpenVideo(video)) },
            onOpenAlbum = { urls, index -> onEvent(UiEvent.OpenAlbum(urls, index)) },
            onOpenPost = { onEvent(UiEvent.OpenPost(post.id)) },
            onReport = { reason, details -> onEvent(UiEvent.Report(post.id, reason, details)) },
            onReportClosed = { onEvent(UiEvent.CloseReport) },
            // Every post here is by the profile's user, so "own post" is whose profile this is.
            onDelete = if (isCurrentUser) ({ onEvent(UiEvent.Delete(post.id)) }) else null,
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

/** Fires an on-demand tab's first load when it becomes visible, and pages it thereafter. */
@Composable
private fun OnDemandTabEffects(
    listState: LazyListState,
    list: ProfilePostList?,
    loadFailed: Boolean,
    onShown: () -> Unit,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }

    LoadMoreEffect(
        listState = listState,
        endReached = list?.endReached ?: true,
        loadMoreFailed = loadFailed,
        onLoadMore = onLoadMore,
    )
}

/**
 * One on-demand tab's rows — Saved or Likes. A null [list] is the state before that tab's first
 * fetch lands, so unlike the posts above it has a loading state of its own. [loadFailed] turns
 * either spinner into a retry: of [onFirstLoad] before the list has loaded, of [onLoadMore] after.
 */
private fun LazyListScope.onDemandTabItems(
    list: ProfilePostList?,
    loadFailed: Boolean,
    onFirstLoad: () -> Unit,
    onLoadMore: () -> Unit,
    reportSend: ReportSendState,
    onEvent: (UiEvent) -> Unit,
    keyPrefix: String,
    @StringRes emptyMessageRes: Int,
) {
    if (list == null) {
        item(key = "$keyPrefix-loading") { LoadMoreFooter(failed = loadFailed, onRetry = onFirstLoad) }
        return
    }

    if (list.posts.isEmpty()) {
        item(key = "$keyPrefix-empty") { EmptyTab(emptyMessageRes) }
        return
    }

    items(list.posts, key = { "$keyPrefix-${it.post.id}" }) { data ->
        PostCard(
            post = data.post,
            author = data.author,
            reportSend = reportSend,
            onToggleLike = { onEvent(UiEvent.ToggleLike(data.post.id)) },
            onToggleBookmark = { onEvent(UiEvent.ToggleBookmark(data.post.id)) },
            // A saved or liked post can be by anyone, so its header opens that author's profile.
            onOpenProfile = { onEvent(UiEvent.OpenProfile(data.author.id)) },
            onOpenVideo = { video -> onEvent(UiEvent.OpenVideo(video)) },
            onOpenAlbum = { urls, index -> onEvent(UiEvent.OpenAlbum(urls, index)) },
            onOpenPost = { onEvent(UiEvent.OpenPost(data.post.id)) },
            onReport = { reason, details ->
                onEvent(UiEvent.Report(data.post.id, reason, details))
            },
            onReportClosed = { onEvent(UiEvent.CloseReport) },
            onDelete = if (data.isOwn) ({ onEvent(UiEvent.Delete(data.post.id)) }) else null,
        )
    }

    if (!list.endReached) {
        item(key = "$keyPrefix-loading-more") { LoadMoreFooter(failed = loadFailed, onRetry = onLoadMore) }
    }
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

/** A plain back button for the loading / not-found states, which have no cover to scrim over. */
@Composable
private fun PlainBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack.rememberDebounced(),
        modifier = modifier
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

/** How far into the header's collapse the bar has finished filling to the surface color. */
private const val BAR_FILL_FRACTION = 0.5f

/**
 * A transparent app bar over the cover that fills to the surface color as the header collapses.
 * Its buttons' scrims fade on the same [progress], so bar and buttons move in lockstep, reaching
 * opaque [BAR_FILL_FRACTION] of the way through the collapse rather than at the very end, so the
 * chrome settles while the cover is still on its way out.
 *
 * Once the tab row pins flush beneath it the bar drops its shadow, which would otherwise fall
 * only across the seam onto the tabs. That is keyed off the *raw* collapse, not [progress],
 * since it tracks where the tabs actually are.
 *
 * [collapse] is read here rather than at the screen scope so a scroll recomposes only the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    userName: String,
    collapse: FloatState,
    maxCollapse: Float,
    onBack: (() -> Unit)?,
) {
    val collapsed = if (maxCollapse > 0f) {
        (collapse.floatValue / maxCollapse).coerceIn(0f, 1f)
    } else {
        0f
    }
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
            reportSend = ReportSendState.IDLE,
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
            reportSend = ReportSendState.IDLE,
            onEvent = {},
        )
    }
}
