package uno.lux.mosaic.home.ui

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
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.ignoreErrors
import uno.lux.mosaic.common.ui.launchReporting
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchCatching
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.common.util.launchRefresh
import uno.lux.mosaic.common.util.stateInWhileSubscribed
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val postRepository: PostRepository,
    userRepository: UserRepository,
    settingsRepository: SettingsRepository,
    private val navigator: Navigator,
    @param:CurrentUserId private val currentUserId: UserId,
) : ViewModel(),
    HomeActions {

    private val _loadError = MutableStateFlow<AppError?>(null)
    private val _loadMoreFailed = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        feedRepository.feedState,
        postRepository.entities,
        userRepository.users,
        _loadError,
        _loadMoreFailed,
    ) { feedState, entities, users, loadError, loadMoreFailed ->
        when (feedState) {
            FeedState.NotLoaded -> {
                if (loadError != null) HomeUiState.Error(loadError) else HomeUiState.Loading
            }

            is FeedState.Loaded -> {
                val cards = feedState.postIds.mapNotNull { id ->
                    val post = entities[id] ?: return@mapNotNull null
                    val author = users[post.authorId] ?: return@mapNotNull null
                    PostCardData(
                        post = post,
                        author = author,
                        isOwn = post.authorId == currentUserId,
                    )
                }
                HomeUiState.Feed(
                    posts = cards,
                    endReached = !feedState.hasMore,
                    refreshError = loadError,
                    loadMoreFailed = loadMoreFailed,
                )
            }
        }
    }.stateInWhileSubscribed(viewModelScope, HomeUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _failedAction = MutableStateFlow<FailedAction?>(null)

    /** The last [FailedAction] to fail here, for the screen to announce. */
    val failedAction: StateFlow<FailedAction?> = _failedAction.asStateFlow()

    private val _reportSend = MutableStateFlow(ReportSendState.IDLE)

    /** How the report dialog's send is going, for the dialog it is still showing under. */
    val reportSend: StateFlow<ReportSendState> = _reportSend.asStateFlow()

    /**
     * Whether the feed may start a video on its own as it scrolls into view.
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

    override fun onRefreshErrorShown() {
        _loadError.value = null
    }

    override fun loadMore() = launchIfIdle(::loadMoreJob) {
        _loadMoreFailed.value = false

        catchErrors(onError = { _loadMoreFailed.value = true }) { feedRepository.loadMore() }
    }

    private suspend fun load() {
        _loadError.value = null
        _loadMoreFailed.value = false

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

    private fun setFailedAction(action: FailedAction) {
        _failedAction.value = action
    }

    private fun setReportSend(state: ReportSendState) {
        _reportSend.value = state
    }

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
