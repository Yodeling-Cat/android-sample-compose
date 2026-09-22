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
import kotlinx.coroutines.launch
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
import uno.lux.mosaic.video.data.domain.Video

/**
 * Holds the profile state for one [userId]. Combines [UserRepository], [ProfileRepository]
 * (counts + ordered post IDs + pagination), and [PostRepository] (entity map) into a single
 * [ProfileScreenData]. Mutations go directly through [PostRepository] so a like toggled here is
 * immediately visible on the home feed — the shared entity store propagates the update to all
 * observers. Navigation intents (opening a post, profile, viewer or the editor, going back) are
 * pushes and pops on the injected [Navigator]. [userId] is a runtime arg wired through [Factory].
 *
 * The Saved and Likes tabs are loaded lazily, on [onSavedTabShown] / [onLikesTabShown]. Saved is
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
) : ViewModel(),
    ProfileActions {

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

    /** The two lazily-loaded tabs, paired so the state combine stays within its typed arity. */
    private data class LazyTabs(
        val bookmarks: ProfilePostList?,
        val likes: ProfilePostList?,
    )

    private val lazyTabs: Flow<LazyTabs> = combine(
        cardListFlow(saved),
        cardListFlow(liked),
    ) { savedPosts, likedPosts -> LazyTabs(savedPosts, likedPosts) }

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

    fun refresh() = launchRefresh(::loadJob, _isRefreshing) { load() }

    fun retry() = launchIfIdle(::loadJob) { load() }

    private suspend fun load() {
        _loadError.value = null

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

    override fun onToggleFollow() = launchReporting(FailedAction.FOLLOW, ::setFailedAction) {
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

    /**
     * The screen has announced [failedAction] and is done with it — spent once shown, so a
     * configuration change cannot announce it again.
     */
    override fun onFailedActionShown() {
        _failedAction.value = null
    }

    override fun loadMorePosts() = launchIfIdle(::loadMorePostsJob) {
        catchErrors { profileRepository.loadMorePosts(userId) }
    }

    /**
     * The Saved tab became visible. Fetches the list the first time only — later visits are
     * served from the repository, and a pull-to-refresh is what re-fetches it.
     */
    override fun onSavedTabShown() = launchIfIdle(::bookmarksJob) {
        catchErrors { saved.ensureLoaded() }
    }

    override fun loadMoreBookmarks() = launchIfIdle(::bookmarksJob) {
        catchErrors { saved.loadMore() }
    }

    /** The Likes tab became visible. Loads once, the way [onSavedTabShown] does. */
    override fun onLikesTabShown() = launchIfIdle(::likesJob) {
        catchErrors { liked.ensureLoaded() }
    }

    override fun loadMoreLikes() = launchIfIdle(::likesJob) {
        catchErrors { liked.loadMore() }
    }

    override fun goBack() = navigator.goBack()

    override fun openEditProfile() = navigator.goToSingleTop(Screen.EditProfile)

    override fun openPost(postId: PostId) = navigator.goTo(Screen.PostDetail(postId))

    override fun openProfile(userId: UserId) = navigator.goTo(Screen.Profile(userId))

    override fun openVideo(video: Video) = navigator.goTo(Screen.FullscreenVideo(video))

    override fun openAlbum(imageUrls: List<String>, initialIndex: Int) =
        navigator.goTo(Screen.AlbumViewer(imageUrls, initialIndex))

    override fun openAvatar(avatarUrl: String) =
        navigator.goTo(Screen.AlbumViewer(listOf(avatarUrl), initialIndex = 0))
}
