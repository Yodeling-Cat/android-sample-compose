package uno.lux.mosaic.post.ui

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.app.di.CurrentUser
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.comment.data.CommentRepository
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.launchReporting
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launch
import uno.lux.mosaic.common.util.launchCatching
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostDetailUiState.Content
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.post.ui.PostDetailUiEvent as UiEvent
import uno.lux.mosaic.post.ui.PostDetailUiState as UiState

@HiltViewModel(assistedFactory = PostDetailViewModel.Factory::class)
class PostDetailViewModel @AssistedInject constructor(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val navigator: Navigator,
    @param:CurrentUser private val currentUser: User,
    @Assisted private val postId: PostId,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(postId: PostId): PostDetailViewModel
    }

    // TODO: Extract, and generalize?

    private sealed interface PostFetch {
        data object Pending : PostFetch

        data object Missing : PostFetch

        data class Failed(
            val error: AppError,
        ) : PostFetch
    }

    private var postFetch: PostFetch = PostFetch.Pending

    private val _uiState = MutableStateFlow(
        UiState(
            content = content(),
            composerUser = currentUser,
        ),
    )

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var commentsJob: Job? = null
    private var loadMoreJob: Job? = null
    private var reportJob: Job? = null

    init {
        observeStores()
        retry()
    }

    fun onEvent(event: UiEvent): Unit = when (event) {
        UiEvent.GoBack -> {
            navigator.goBack()
        }

        is UiEvent.OpenProfile -> {
            navigator.goTo(Screen.Profile(event.userId))
        }

        is UiEvent.OpenVideo -> {
            navigator.goTo(Screen.FullscreenVideo(event.video))
        }

        is UiEvent.OpenAlbum -> {
            navigator.goTo(
                Screen.AlbumViewer(event.imageUrls, event.initialIndex),
            )
        }

        UiEvent.ToggleLike -> {
            toggleLike()
        }

        UiEvent.ToggleBookmark -> {
            toggleBookmark()
        }

        UiEvent.Delete -> {
            delete()
        }

        is UiEvent.Report -> {
            report(event.reason, event.details)
        }

        UiEvent.CloseReport -> {
            dropReport(::reportJob, ::setReportSend)
        }

        UiEvent.Retry -> {
            retry()
        }

        is UiEvent.AddComment -> {
            addComment(event.text)
        }

        is UiEvent.ToggleCommentLike -> {
            toggleCommentLike(event.commentId)
        }

        UiEvent.LoadMoreComments -> {
            loadMoreComments()
        }

        UiEvent.RetryComments -> {
            retryComments()
        }

        UiEvent.FailedActionShown -> {
            _uiState.update { it.copy(failedAction = null) }
        }

        UiEvent.CommentSent -> {
            setCommentSend(CommentSendState.IDLE)
        }

        UiEvent.ScrolledToComment -> {
            mutateCommentThread { it.copy(scrollTo = null) }
        }
    }

    private fun setCommentSend(state: CommentSendState) = _uiState.update {
        it.copy(commentSend = state)
    }

    private fun setReportSend(state: ReportSendState) = _uiState.update {
        it.copy(reportSend = state)
    }

    private fun setFailedAction(action: FailedAction) = _uiState.update {
        it.copy(failedAction = action)
    }

    private fun observeStores() = launch {
        combine(
            postRepository.entities,
            userRepository.users,
            postRepository.deletedIds,
        ) { _, _, _ -> }.collect { updateContent() }
    }

    private fun mutateCommentThread(mutate: (CommentThread) -> CommentThread) = _uiState.update {
        it.copy(commentThread = mutate(it.commentThread))
    }

    private fun updateContent() = _uiState.update { it.copy(content = content()) }

    private fun content(): Content {
        val post = postRepository.entities.value[postId]
        val author = post?.let { userRepository.users.value[it.authorId] }

        if (post != null && author != null) {
            return Content.Loaded(
                post = post,
                author = author,
                isOwn = post.authorId == currentUser.id,
            )
        }

        // TODO: Sounds like the wrong pattern. I'd rather expect to get a 404 error from the backend.
        //  What even would happen if the entity was deleted from the backend? Does this pattern only handle locally deleted posts?
        // Told by the store that the post is gone, whichever screen deleted it. Without this the
        // emptied store would read as "not loaded yet" and leave the page spinning.
        if (postId in postRepository.deletedIds.value) return Content.NotFound

        return when (val fetch = postFetch) {
            PostFetch.Pending -> Content.Loading
            PostFetch.Missing -> Content.NotFound
            is PostFetch.Failed -> Content.Error(fetch.error)
        }
    }

    private fun setPostFetch(outcome: PostFetch) {
        postFetch = outcome
        updateContent()
    }

    private fun retry() {
        launchIfIdle(::loadJob) { loadPost() }
        retryComments()
    }

    private suspend fun loadPost() {
        if (_uiState.value.content is Content.Loaded) return

        setPostFetch(PostFetch.Pending)
        catchErrors(onError = { e ->
            setPostFetch(PostFetch.Failed(e.toAppError()))
        }, {
            // TODO: 404 can also mean that it doesn't exist *yet*, not that it was deleted, or perhaps its temporarily hidden from
            //  the current user by privacy settings. We might thus want to retry.
            // A 404 is the server saying the post is gone — an answer, not a failure to retry.
            if (postRepository.load(postId) == null) setPostFetch(PostFetch.Missing)
        })
    }

    private fun toggleLike() = launchCatching {
        postRepository.toggleLike(postId)
    }

    private fun toggleBookmark() = launchCatching {
        postRepository.toggleBookmark(postId)
    }

    private fun delete() = launchReporting(FailedAction.DELETE_POST, ::setFailedAction) {
        // A failed delete throws before the pop, so the page stays put and announces the failure.
        postRepository.delete(postId)
        navigator.goBack()
    }

    private fun report(reason: ReportReason, details: String) =
        launchReport(::reportJob, ::setReportSend) {
            postRepository.report(postId, reason, details)
        }

    private fun retryComments() = launchIfIdle(::commentsJob) { loadComments() }

    private suspend fun loadComments() {
        mutateCommentThread { it.copy(isLoading = true, error = null, loadMoreFailed = false) }

        try {
            catchErrors(onError = { e -> mutateCommentThread { it.copy(error = e.toAppError()) } }) {
                val page = commentRepository.loadComments(postId, cursor = null)
                mutateCommentThread {
                    it.copy(
                        comments = page.comments,
                        nextCursor = page.nextCursor,
                        endReached = !page.hasMore,
                        scrollTo = null,
                    )
                }
            }
        } finally {
            mutateCommentThread { it.copy(isLoading = false) }
        }
    }

    private fun loadMoreComments() = launchIfIdle(::loadMoreJob) {
        val cursor = _uiState.value.commentThread.nextCursor ?: return@launchIfIdle
        mutateCommentThread { it.copy(loadMoreFailed = false) }

        catchErrors(onError = {
            // Only a failure of the page the thread is still waiting for; one a reload has moved
            // past has nothing left to retry.
            mutateCommentThread { thread ->
                if (thread.nextCursor == cursor) thread.copy(loadMoreFailed = true) else thread
            }
        }) {
            val page = commentRepository.loadComments(postId, cursor)
            mutateCommentThread { thread ->
                // A reload that landed while this page was on the wire started the thread over, so
                // this window no longer follows what is on screen and is dropped rather than glued on.
                if (thread.nextCursor != cursor) {
                    thread
                } else {
                    thread.copy(
                        comments = thread.comments + page.comments,
                        nextCursor = page.nextCursor,
                        endReached = !page.hasMore,
                    )
                }
            }
        }
    }

    private fun toggleCommentLike(commentId: CommentId) = launchCatching {
        val thread = _uiState.value.commentThread
        val before = thread.comments.find { it.id == commentId } ?: return@launchCatching
        val liked = !before.isLiked
        val delta = if (liked) 1 else -1
        // A later tap has already moved past this request, so its answer is the one to trust.
        val stillOurs = { comment: Comment -> comment.isLiked == liked }

        updateComment(commentId) {
            it.copy(isLiked = liked, likeCount = it.likeCount + delta)
        }

        try {
            val confirmed = commentRepository.setLike(postId, commentId, liked)
            updateComment(commentId, stillOurs) {
                it.copy(isLiked = confirmed.isLiked, likeCount = confirmed.likeCount)
            }
        } catch (e: CancellationException) {
            // The screen went away, not the request: the server most likely took it, so the
            // optimistic value is the better guess to leave behind.
            throw e
        } catch (e: Exception) {
            // One like back off the count as it stands, the way PostRepository.toggleLike does.
            updateComment(commentId, stillOurs) {
                it.copy(isLiked = before.isLiked, likeCount = it.likeCount - delta)
            }
            throw e
        }
    }

    private fun updateComment(
        commentId: CommentId,
        predicate: (Comment) -> Boolean = { true },
        mutate: (Comment) -> Comment,
    ) = mutateCommentThread { thread ->
        thread.copy(
            comments = thread.comments.map { comment ->
                if (comment.id == commentId && predicate(comment)) mutate(comment) else comment
            },
        )
    }

    private fun addComment(text: String) = launchReporting(FailedAction.SEND_COMMENT, ::setFailedAction) {
        setCommentSend(CommentSendState.SENDING)

        try {
            val comment = commentRepository.addComment(postId, text)
            mutateCommentThread {
                it.copy(comments = listOf(comment) + it.comments, scrollTo = comment.id)
            }
            postRepository.commentAdded(postId)
            setCommentSend(CommentSendState.SENT)
        } catch (e: Exception) {
            setCommentSend(CommentSendState.IDLE)
            throw e
        }
    }
}
