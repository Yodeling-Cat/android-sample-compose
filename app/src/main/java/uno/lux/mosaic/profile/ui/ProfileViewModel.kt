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
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.launchReporting
import uno.lux.mosaic.common.util.EntityFetch
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchCatching
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.common.util.launchRefresh
import uno.lux.mosaic.common.util.stateInWhileSubscribed
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.post.ui.PostReport
import uno.lux.mosaic.post.ui.PostReporter
import uno.lux.mosaic.profile.data.PostList
import uno.lux.mosaic.profile.data.ProfileRepository
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.profile.ui.ProfileUiEvent as UiEvent
import uno.lux.mosaic.profile.ui.ProfileUiState as UiState

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

    private val _userFetch = MutableStateFlow<EntityFetch>(EntityFetch.Pending)

    private val bookmarks = profileRepository.bookmarks(userId)
    private val likes = profileRepository.likes(userId)

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

    private val _loadFailures = MutableStateFlow(LoadFailures())

    private val lazyTabs = combine(cardListFlow(bookmarks), cardListFlow(likes), _loadFailures, ::LazyTabs)

    private val ownPosts = combine(
        profileRepository.postIds(userId),
        profileRepository.hasMorePosts(userId),
        postRepository.entities,
    ) { ids, hasMore, entities ->
        OwnPosts(posts = ids.mapNotNull { entities[it] }, endReached = !hasMore)
    }

    // null until the user is in the store
    private val loaded = combine(
        userRepository.user(userId),
        profileRepository.profile(userId),
        ownPosts,
        lazyTabs,
    ) { user, profile, own, tabs ->
        user?.let {
            UiState.Loaded(
                data = ProfileScreenData(
                    user = it,
                    profile = profile,
                    posts = own.posts,
                    postsEndReached = own.endReached,
                    bookmarks = tabs.bookmarks,
                    likes = tabs.likes,
                    postsLoadMoreFailed = tabs.failures.posts,
                    bookmarksLoadFailed = tabs.failures.bookmarks,
                    likesLoadFailed = tabs.failures.likes,
                ),
                isCurrentUser = userId == currentUserId,
            )
        }
    }

    val uiState: StateFlow<UiState> = combine(loaded, _userFetch) { loaded, fetch ->
        loaded ?: when (fetch) {
            // Absent from the stores means "no such user" only once a fetch has actually run.
            // Before that it means nothing has asked yet — which is every cold start, since a
            // profile restored after process death begins with empty stores.
            EntityFetch.Pending -> UiState.Loading
            EntityFetch.Done -> UiState.NotFound
            is EntityFetch.Failed -> UiState.Error(fetch.error)
        }
    }.stateInWhileSubscribed(viewModelScope, UiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _failedAction = MutableStateFlow<FailedAction?>(null)

    val failedAction: StateFlow<FailedAction?> = _failedAction.asStateFlow()

    private val _report = MutableStateFlow<PostReport?>(null)

    val report: StateFlow<PostReport?> = _report.asStateFlow()

    private val reporter = PostReporter(viewModelScope, postRepository, _report)

    private var loadJob: Job? = null
    private var loadMorePostsJob: Job? = null
    private var bookmarksJob: Job? = null
    private var likesJob: Job? = null

    init {
        retry()
    }

    fun onEvent(event: UiEvent): Unit = when (event) {
        UiEvent.Refresh -> {
            refresh()
        }

        UiEvent.Retry -> {
            retry()
        }

        is UiEvent.ToggleLike -> {
            toggleLike(event.postId)
        }

        is UiEvent.ToggleBookmark -> {
            toggleBookmark(event.postId)
        }

        is UiEvent.Delete -> {
            delete(event.postId)
        }

        is UiEvent.OpenReport -> {
            reporter.open(event.postId)
        }

        is UiEvent.ReportDialog -> {
            reporter.onEvent(event.event)
        }

        UiEvent.ToggleFollow -> {
            toggleFollow()
        }

        UiEvent.FailedActionShown -> {
            _failedAction.value = null
        }

        UiEvent.LoadMorePosts -> {
            loadMorePosts()
        }

        UiEvent.BookmarksTabShown -> {
            ensureBookmarksLoaded()
        }

        UiEvent.LoadMoreBookmarks -> {
            loadMoreBookmarks()
        }

        UiEvent.LikesTabShown -> {
            ensureLikesLoaded()
        }

        UiEvent.LoadMoreLikes -> {
            loadMoreLikes()
        }

        UiEvent.GoBack -> {
            navigator.goBack()
        }

        UiEvent.OpenEditProfile -> {
            navigator.goToSingleTop(Screen.EditProfile)
        }

        is UiEvent.OpenPost -> {
            navigator.goTo(Screen.PostDetail(event.postId))
        }

        is UiEvent.OpenProfile -> {
            navigator.goTo(Screen.Profile(event.userId))
        }

        is UiEvent.OpenVideo -> {
            navigator.goTo(Screen.FullscreenVideo(event.video))
        }

        is UiEvent.OpenAlbum -> {
            navigator.goTo(Screen.AlbumViewer(event.imageUrls, event.initialIndex))
        }

        is UiEvent.OpenAvatar -> {
            navigator.goTo(Screen.AlbumViewer(listOf(event.avatarUrl), initialIndex = 0))
        }
    }

    private fun refresh() = launchRefresh(::loadJob, _isRefreshing) { load() }

    private fun retry() = launchIfIdle(::loadJob) { load() }

    private suspend fun load() {
        _userFetch.value = EntityFetch.Pending
        // A refresh restarts every list it re-fetches, so a page that failed to follow the old one
        // is moot. A tab that never loaded is not re-fetched, so its failure still stands.
        val bookmarksLoaded = bookmarks.ids.first() != null
        val likesLoaded = likes.ids.first() != null
        _loadFailures.update {
            LoadFailures(
                bookmarks = it.bookmarks && !bookmarksLoaded,
                likes = it.likes && !likesLoaded,
            )
        }

        catchErrors(onError = { e -> _userFetch.value = EntityFetch.Failed(e.toAppError()) }) {
            coroutineScope {
                launch { userRepository.refresh(userId) }
                launch { profileRepository.refresh(userId) }
                // Each is a no-op for a tab nobody opened: a profile load must not reach for a
                // list the user has not asked to see, least of all the private one.
                launch { bookmarks.refreshIfLoaded() }
                launch { likes.refreshIfLoaded() }
            }
            _userFetch.value = EntityFetch.Done
        }
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

    private fun toggleFollow() = launchReporting(FailedAction.FOLLOW, ::setFailedAction) {
        userRepository.toggleFollow(userId)
    }

    private fun setFailedAction(action: FailedAction) {
        _failedAction.value = action
    }

    private fun loadMorePosts() = launchIfIdle(::loadMorePostsJob) {
        trackingFailure({ copy(posts = it) }) { profileRepository.loadMorePosts(userId) }
    }

    private fun ensureBookmarksLoaded() = launchIfIdle(::bookmarksJob) {
        trackingFailure({ copy(bookmarks = it) }) { bookmarks.ensureLoaded() }
    }

    private fun loadMoreBookmarks() = launchIfIdle(::bookmarksJob) {
        trackingFailure({ copy(bookmarks = it) }) { bookmarks.loadMore() }
    }

    private fun ensureLikesLoaded() = launchIfIdle(::likesJob) {
        trackingFailure({ copy(likes = it) }) { likes.ensureLoaded() }
    }

    private fun loadMoreLikes() = launchIfIdle(::likesJob) {
        trackingFailure({ copy(likes = it) }) { likes.loadMore() }
    }

    private suspend fun trackingFailure(
        mark: LoadFailures.(failed: Boolean) -> LoadFailures,
        block: suspend () -> Unit,
    ) {
        _loadFailures.update { it.mark(false) }

        catchErrors(onError = { _loadFailures.update { it.mark(true) } }, block = block)
    }
}

private data class LoadFailures(
    val posts: Boolean = false,
    val bookmarks: Boolean = false,
    val likes: Boolean = false,
)

private data class LazyTabs(
    val bookmarks: ProfilePostList?,
    val likes: ProfilePostList?,
    val failures: LoadFailures,
)

private data class OwnPosts(
    val posts: List<Post>,
    val endReached: Boolean,
)
