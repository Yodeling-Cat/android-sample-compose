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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val postRepository: PostRepository,
    userRepository: UserRepository,
    settingsRepository: SettingsRepository,
    private val navigator: Navigator,
    @param:CurrentUserId private val currentUserId: UserId,
) : ViewModel() {

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

    fun onEvent(event: HomeUiEvent): Unit = when (event) {
        HomeUiEvent.Refresh -> {
            refresh()
        }

        HomeUiEvent.Retry -> {
            retry()
        }

        HomeUiEvent.RefreshErrorShown -> {
            _loadError.value = null
        }

        HomeUiEvent.FailedActionShown -> {
            _failedAction.value = null
        }

        HomeUiEvent.LoadMore -> {
            loadMore()
        }

        is HomeUiEvent.ToggleLike -> {
            toggleLike(event.postId)
        }

        is HomeUiEvent.ToggleBookmark -> {
            toggleBookmark(event.postId)
        }

        is HomeUiEvent.Delete -> {
            delete(event.postId)
        }

        is HomeUiEvent.Report -> {
            report(event.postId, event.reason, event.details)
        }

        HomeUiEvent.CloseReport -> {
            dropReport(::reportJob, ::setReportSend)
        }

        HomeUiEvent.OpenSettings -> {
            navigator.goToSingleTop(Screen.Settings)
        }

        is HomeUiEvent.OpenProfile -> {
            navigator.goTo(Screen.Profile(event.userId))
        }

        is HomeUiEvent.OpenPost -> {
            navigator.goTo(Screen.PostDetail(event.postId))
        }

        is HomeUiEvent.OpenVideo -> {
            navigator.goTo(Screen.FullscreenVideo(event.video))
        }

        is HomeUiEvent.OpenAlbum -> {
            navigator.goTo(Screen.AlbumViewer(event.imageUrls, event.initialIndex))
        }
    }

    private fun refresh() = launchRefresh(::loadJob, _isRefreshing) { load() }

    private fun retry() = launchIfIdle(::loadJob) {
        // Back to NotLoaded first, so a retry after a loaded feed shows the spinner rather than
        // the stale list it is replacing.
        feedRepository.reset()
        load()
    }

    private fun loadMore() = launchIfIdle(::loadMoreJob) {
        _loadMoreFailed.value = false

        catchErrors(onError = { _loadMoreFailed.value = true }) { feedRepository.loadMore() }
    }

    private suspend fun load() {
        _loadError.value = null
        _loadMoreFailed.value = false

        ignoreErrors(_loadError) { feedRepository.refresh() }
    }

    private fun toggleLike(postId: PostId) = launchCatching {
        postRepository.toggleLike(postId)
    }

    private fun toggleBookmark(postId: PostId) = launchCatching {
        postRepository.toggleBookmark(postId)
    }

    private fun delete(postId: PostId) = launchReporting(FailedAction.DELETE_POST, ::setFailedAction) {
        postRepository.delete(postId)
    }

    private fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    ) = launchReport(::reportJob, ::setReportSend) {
        postRepository.report(postId, reason, details)
    }

    private fun setFailedAction(action: FailedAction) {
        _failedAction.value = action
    }

    private fun setReportSend(state: ReportSendState) {
        _reportSend.value = state
    }
}
