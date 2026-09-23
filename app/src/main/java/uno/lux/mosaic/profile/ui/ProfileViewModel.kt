package uno.lux.mosaic.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.post.ui.dropReport
import uno.lux.mosaic.post.ui.launchReport
import uno.lux.mosaic.profile.data.PostList
import uno.lux.mosaic.profile.data.ProfileRepository
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.UserId

/**
 * Holds the profile state for one [userId]. Combines [UserRepository], [ProfileRepository]
 * (counts + ordered post IDs + pagination), and [PostRepository] (entity map) into a single
 * [ProfileScreenData]. Mutations go directly through [PostRepository] so a like toggled here is
 * immediately visible on the home feed — the shared entity store propagates the update to all
 * observers. Navigation intents (opening a post, profile, viewer or the editor, going back) are
 * pushes and pops on the injected [Navigator]. [userId] is a runtime arg wired through [Factory].
 *
 * The Saved and Likes tabs are loaded lazily, on [ProfileUiEvent.SavedTabShown] /
 * [ProfileUiEvent.LikesTabShown]. Saved is
 * private: the screen offers that tab only on the signed-in user's own profile, and the server
 * refuses the list to anyone else regardless. Likes are public, and load the same way only
 * because a tab nobody opened should cost no request.
 */
@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    private val profileRepository: ProfileRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val navigator: Navigator,
    @param:CurrentUserId private val currentUserId: UserId,
    @Assisted private val userId: UserId,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(userId: UserId): ProfileViewModel
    }

    private val _loadError = MutableStateFlow<AppError?>(null)

    /** Whether a load has run at all, which is what makes an absent user mean "no such user". */
    private val _hasLoaded = MutableStateFlow(false)

    /** This profile's two on-demand tabs, bound to [userId] once rather than at every call. */
    private val saved = profileRepository.saved(userId)
    private val liked = profileRepository.liked(userId)

    /**
     * Resolves one on-demand tab's post IDs into cards, staying null until that tab's first load
     * lands.
     */
    private fun cardListFlow(list: PostList): Flow<ProfilePostList?> =
        combine(
            list.ids,
            list.hasMore,
            postRepository.entities,
            userRepository.users,
        ) { postIds, more, entities, users ->
            if (postIds == null) return@combine null

            val cards = postIds.mapNotNull { id ->
                val post = entities[id] ?: return@mapNotNull null
                val author = users[post.authorId] ?: return@mapNotNull null
                PostCardData(
                    post = post,
                    author = author,
                    isOwn = post.authorId == currentUserId,
                )
            }

            ProfilePostList(posts = cards, endReached = !more)
        }

    /** Which of the three lists failed the last time it was asked for a page. */
    private data class LoadFailures(
        val posts: Boolean = false,
        val bookmarks: Boolean = false,
        val likes: Boolean = false,
    )

    private val _loadFailures = MutableStateFlow(LoadFailures())

    /**
     * The two lazily-loaded tabs and the lists' load failures, bundled so the state combine stays
     * within its typed arity.
     */
    private data class LazyTabs(
        val bookmarks: ProfilePostList?,
        val likes: ProfilePostList?,
        val failures: LoadFailures,
    )

    private val lazyTabs: Flow<LazyTabs> = combine(
        cardListFlow(saved),
        cardListFlow(liked),
        _loadFailures,
    ) { savedPosts, likedPosts, failures -> LazyTabs(savedPosts, likedPosts, failures) }

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(
            userRepository.user(userId),
            profileRepository.profile(userId),
            combine(profileRepository.postIds(userId), postRepository.entities) { ids, entities ->
                ids.mapNotNull { entities[it] }
            },
            profileRepository.hasMorePosts(userId),
            lazyTabs,
        ) { user, profile, posts, hasMorePosts, tabs ->
            if (user == null) {
                null
            } else {
                ProfileUiState.Loaded(
                    data = ProfileScreenData(
                        user = user,
                        profile = profile,
                        posts = posts,
                        postsEndReached = !hasMorePosts,
                        bookmarks = tabs.bookmarks,
                        likes = tabs.likes,
                        postsLoadMoreFailed = tabs.failures.posts,
                        bookmarksLoadFailed = tabs.failures.bookmarks,
                        likesLoadFailed = tabs.failures.likes,
                    ),
                    isCurrentUser = userId == currentUserId,
                )
            }
        },
        _loadError,
        _hasLoaded,
    ) { state, loadError, hasLoaded ->
        when {
            state != null -> state

            loadError != null -> ProfileUiState.Error(loadError)

            // Absent from the stores means "no such user" only once a fetch has actually run.
            // Before that it means nothing has asked yet — which is every cold start, since a
            // profile restored after process death begins with empty stores.
            hasLoaded -> ProfileUiState.NotFound

            else -> ProfileUiState.Loading
        }
    }.stateInWhileSubscribed(viewModelScope, ProfileUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _failedAction = MutableStateFlow<FailedAction?>(null)

    /** The last [FailedAction] to fail here, for the screen to announce once and then spend. */
    val failedAction: StateFlow<FailedAction?> = _failedAction.asStateFlow()

    private val _reportSend = MutableStateFlow(ReportSendState.IDLE)

    /** How the report dialog's send is going, for the dialog it is still showing under. */
    val reportSend: StateFlow<ReportSendState> = _reportSend.asStateFlow()

    private var loadJob: Job? = null
    private var loadMorePostsJob: Job? = null
    private var bookmarksJob: Job? = null
    private var likesJob: Job? = null
    private var reportJob: Job? = null

    init {
        retry()
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            ProfileUiEvent.Refresh -> {
                refresh()
            }

            ProfileUiEvent.Retry -> {
                retry()
            }

            is ProfileUiEvent.ToggleLike -> {
                toggleLike(event.postId)
            }

            is ProfileUiEvent.ToggleBookmark -> {
                toggleBookmark(event.postId)
            }

            is ProfileUiEvent.Delete -> {
                delete(event.postId)
            }

            is ProfileUiEvent.Report -> {
                report(event.postId, event.reason, event.details)
            }

            ProfileUiEvent.CloseReport -> {
                dropReport(::reportJob, ::setReportSend)
            }

            ProfileUiEvent.ToggleFollow -> {
                toggleFollow()
            }

            ProfileUiEvent.FailedActionShown -> {
                _failedAction.value = null
            }

            ProfileUiEvent.LoadMorePosts -> {
                loadMorePosts()
            }

            ProfileUiEvent.SavedTabShown -> {
                ensureSavedLoaded()
            }

            ProfileUiEvent.LoadMoreBookmarks -> {
                loadMoreBookmarks()
            }

            ProfileUiEvent.LikesTabShown -> {
                ensureLikesLoaded()
            }

            ProfileUiEvent.LoadMoreLikes -> {
                loadMoreLikes()
            }

            ProfileUiEvent.GoBack -> {
                navigator.goBack()
            }

            ProfileUiEvent.OpenEditProfile -> {
                navigator.goToSingleTop(Screen.EditProfile)
            }

            is ProfileUiEvent.OpenPost -> {
                navigator.goTo(Screen.PostDetail(event.postId))
            }

            is ProfileUiEvent.OpenProfile -> {
                navigator.goTo(Screen.Profile(event.userId))
            }

            is ProfileUiEvent.OpenVideo -> {
                navigator.goTo(Screen.FullscreenVideo(event.video))
            }

            is ProfileUiEvent.OpenAlbum -> {
                navigator.goTo(Screen.AlbumViewer(event.imageUrls, event.initialIndex))
            }

            is ProfileUiEvent.OpenAvatar -> {
                navigator.goTo(Screen.AlbumViewer(listOf(event.avatarUrl), initialIndex = 0))
            }
        }
    }

    private fun refresh() = launchRefresh(::loadJob, _isRefreshing) { load() }

    private fun retry() = launchIfIdle(::loadJob) { load() }

    private suspend fun load() {
        _loadError.value = null
        // A refresh restarts every list it re-fetches, so a page that failed to follow the old one
        // is moot. A tab that never loaded is not re-fetched, so its failure still stands.
        val savedLoaded = saved.ids.first() != null
        val likedLoaded = liked.ids.first() != null
        _loadFailures.update {
            LoadFailures(
                bookmarks = it.bookmarks && !savedLoaded,
                likes = it.likes && !likedLoaded,
            )
        }

        ignoreErrors(_loadError) {
            coroutineScope {
                launch { userRepository.refresh(userId) }
                launch { profileRepository.refresh(userId) }
                // Each is a no-op for a tab nobody opened: a profile load must not reach for a
                // list the user has not asked to see, least of all the private one.
                launch { saved.refreshIfLoaded() }
                launch { liked.refreshIfLoaded() }
            }
        }

        _hasLoaded.value = true
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

    private fun toggleFollow() = launchReporting(FailedAction.FOLLOW, ::setFailedAction) {
        userRepository.toggleFollow(userId)
    }

    /** Passed to [launchReporting] by reference, which is why it is a function. */
    private fun setFailedAction(action: FailedAction) {
        _failedAction.value = action
    }

    /** Passed to [launchReport] and [dropReport] by reference, which is why it is a function. */
    private fun setReportSend(state: ReportSendState) {
        _reportSend.value = state
    }

    private fun loadMorePosts() = launchIfIdle(::loadMorePostsJob) {
        trackingFailure({ copy(posts = it) }) { profileRepository.loadMorePosts(userId) }
    }

    private fun ensureSavedLoaded() = launchIfIdle(::bookmarksJob) {
        trackingFailure({ copy(bookmarks = it) }) { saved.ensureLoaded() }
    }

    private fun loadMoreBookmarks() = launchIfIdle(::bookmarksJob) {
        trackingFailure({ copy(bookmarks = it) }) { saved.loadMore() }
    }

    private fun ensureLikesLoaded() = launchIfIdle(::likesJob) {
        trackingFailure({ copy(likes = it) }) { liked.ensureLoaded() }
    }

    private fun loadMoreLikes() = launchIfIdle(::likesJob) {
        trackingFailure({ copy(likes = it) }) { liked.loadMore() }
    }

    /**
     * Runs one list's page load, recording in [_loadFailures] whether it failed, through [mark],
     * which names the list. Every attempt starts by clearing the mark, so a retry re-arms the
     * list's paging the moment it begins.
     */
    private suspend fun trackingFailure(
        mark: LoadFailures.(failed: Boolean) -> LoadFailures,
        block: suspend () -> Unit,
    ) {
        _loadFailures.update { it.mark(false) }

        catchErrors(onError = { _loadFailures.update { it.mark(true) } }, block = block)
    }
}
