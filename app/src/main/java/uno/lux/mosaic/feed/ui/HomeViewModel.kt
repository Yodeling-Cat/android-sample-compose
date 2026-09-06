package uno.lux.mosaic.feed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import uno.lux.mosaic.app.di.CurrentUserId
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.app.util.AppError
import uno.lux.mosaic.app.util.catchErrors
import uno.lux.mosaic.app.util.launchCatching
import uno.lux.mosaic.app.util.launchIfIdle
import uno.lux.mosaic.app.util.launchRefresh
import uno.lux.mosaic.app.util.stateInWhileSubscribed
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.ignoreErrors
import uno.lux.mosaic.common.ui.launchReporting
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.feed.data.FeedState
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.post.ui.dropReport
import uno.lux.mosaic.post.ui.launchReport
import uno.lux.mosaic.settings.data.SettingsRepository
import uno.lux.mosaic.settings.data.domain.DEFAULT_AUTO_PLAY_VIDEOS
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video
import javax.inject.Inject

/**
 * Holds feed state and translates user intent — likes and bookmarks into repository mutations,
 * opening a post/profile/viewer into pushes on the injected [Navigator]. Combines
 * [FeedRepository] (feed state + pagination), [PostRepository] (entity map), and
 * [UserRepository] (author lookup) to produce [PostCardData] items ready for display. All
 * collaborators are constructor dependencies so the ViewModel can be unit tested against fakes
 * (the navigator against a plain attached list).
 *
 * [FeedState.NotLoaded] is the initial state of [FeedRepository.feedState]; the combine maps it
 * to [HomeUiState.Loading] until [FeedRepository.refresh] completes and emits [FeedState.Loaded].
 * Because entities and users are ingested before that emission, the Loading → Feed transition is
 * atomic: there is no intermediate state where the flag flips but the data has not yet arrived.
 *
 * **A loaded feed outranks a load error.** The state a failure lands in is decided by whether
 * there is anything to read: with nothing loaded it becomes [HomeUiState.Error] and owns the
 * screen, while over a loaded feed it rides along as [HomeUiState.Feed.refreshError] for the
 * screen to show transiently — a refresh that failed must not take away the posts the user was
 * reading. [retry] is the one path back to a full-screen error, because it resets the feed first.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    settingsRepository: SettingsRepository,
    private val navigator: Navigator,
    @param:CurrentUserId private val currentUserId: UserId,
) : ViewModel(),
    HomeActions {

    private val _loadError = MutableStateFlow<AppError?>(null)

    // Preserves PostCardData instances across emissions so Compose strong-skipping can use
    // reference equality to skip PostCard recomposition for posts that didn't change.
    private var cardCache = emptyMap<PostId, PostCardData>()

    val uiState: StateFlow<HomeUiState> = combine(
        feedRepository.feedState,
        postRepository.entities,
        userRepository.users,
        _loadError,
    ) { feedState, entities, users, loadError ->
        when (feedState) {
            FeedState.NotLoaded -> {
                // Nothing loaded, so a failed load is all there is to show.
                if (loadError != null) HomeUiState.Error(loadError) else HomeUiState.Loading
            }

            is FeedState.Loaded -> {
                val cards = feedState.postIds.mapNotNull { id ->
                    val post = entities[id] ?: return@mapNotNull null
                    val author = users[post.authorId] ?: return@mapNotNull null
                    val cached = cardCache[id]
                    if (cached != null && cached.post === post && cached.author === author) {
                        cached
                    } else {
                        PostCardData(
                            post = post,
                            author = author,
                            isOwn = post.authorId == currentUserId,
                        )
                    }
                }
                cardCache = cards.associateBy { it.post.id }
                HomeUiState.Feed(
                    posts = cards,
                    endReached = !feedState.hasMore,
                    refreshError = loadError,
                )
            }
        }
    }.stateInWhileSubscribed(viewModelScope, HomeUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _failedAction = MutableStateFlow<FailedAction?>(null)

    /** The last [FailedAction] to fail here, for the screen to announce once and then spend. */
    val failedAction: StateFlow<FailedAction?> = _failedAction.asStateFlow()

    private val _reportSend = MutableStateFlow(ReportSendState.IDLE)

    /** How the report dialog's send is going, for the dialog it is still showing under. */
    val reportSend: StateFlow<ReportSendState> = _reportSend.asStateFlow()

    /**
     * Whether the feed may start a video on its own as it scrolls into view. The stored preference
     * arrives asynchronously, so this starts from [DEFAULT_AUTO_PLAY_VIDEOS] rather than a literal of
     * its own — otherwise a launch would briefly disagree with the repository about what an
     * unset preference means, and the feed would flip playback as the stored choice landed.
     */
    val autoPlayVideos: StateFlow<Boolean> = settingsRepository.autoPlayVideos
        .stateInWhileSubscribed(viewModelScope, DEFAULT_AUTO_PLAY_VIDEOS)

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var reportJob: Job? = null

    init {
        retry()
    }

    override fun refresh() = launchRefresh(::loadJob, _isRefreshing) { load() }

    override fun retry() = launchIfIdle(::loadJob) {
        // Back to NotLoaded first, so a retry after a loaded feed shows the spinner rather than
        // the stale list it is replacing.
        feedRepository.reset()
        load()
    }

    /**
     * The screen has announced the transient [HomeUiState.Feed.refreshError] and is done with it.
     * Clearing it here rather than leaving it to stand until the next load is what stops a
     * configuration change from re-announcing a failure the user has already read: the composition
     * is rebuilt from nothing, so an error still in the state would be shown again on every
     * rotation. The full-screen [HomeUiState.Error] is never spent this way — it *is* the screen,
     * and only [retry] leaves it.
     */
    override fun onRefreshErrorShown() {
        _loadError.value = null
    }

    override fun loadMore() = launchIfIdle(::loadMoreJob) {
        catchErrors { feedRepository.loadMore() }
    }

    private suspend fun load() {
        _loadError.value = null

        ignoreErrors(_loadError) { feedRepository.refresh() }
    }

    override fun onToggleLike(postId: PostId) = launchCatching {
        postRepository.toggleLike(postId)
    }

    override fun onToggleBookmark(postId: PostId) = launchCatching {
        postRepository.toggleBookmark(postId)
    }

    override fun onDeletePost(postId: PostId) = launchReporting(FailedAction.DELETE_POST, ::setFailedAction) {
        postRepository.delete(postId)
    }

    override fun onReportPost(
        postId: PostId,
        reason: ReportReason,
        details: String,
    ) = launchReport(::reportJob, ::setReportSend) {
        postRepository.report(postId, reason, details)
    }

    override fun onReportClosed() = dropReport(::reportJob, ::setReportSend)

    /** Passed to [launchReporting] by reference, which is why it is a function. */
    private fun setFailedAction(action: FailedAction) {
        _failedAction.value = action
    }

    /** Passed to [launchReport] and [dropReport] by reference, which is why it is a function. */
    private fun setReportSend(state: ReportSendState) {
        _reportSend.value = state
    }

    /**
     * The screen has announced [failedAction] and is done with it — the same spend-once lifetime
     * [onRefreshErrorShown] gives the refresh error.
     */
    override fun onFailedActionShown() {
        _failedAction.value = null
    }

    override fun openSettings() = navigator.goToSingleTop(Screen.Settings)

    override fun openProfile(userId: UserId) = navigator.goTo(Screen.Profile(userId))

    override fun openPost(postId: PostId) = navigator.goTo(Screen.PostDetail(postId))

    override fun openVideo(video: Video) = navigator.goTo(Screen.FullscreenVideo(video))

    override fun openAlbum(imageUrls: List<String>, initialIndex: Int) =
        navigator.goTo(Screen.AlbumViewer(imageUrls, initialIndex))
}
