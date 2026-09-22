package uno.lux.mosaic.post.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uno.lux.mosaic.app.di.CurrentUser
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.app.util.AppError
import uno.lux.mosaic.app.util.catchErrors
import uno.lux.mosaic.app.util.launchCatching
import uno.lux.mosaic.app.util.launchIfIdle
import uno.lux.mosaic.comment.data.CommentRepository
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.ui.launchReporting
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostDetailUiState.Content
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User

/**
 * Holds the state for a single post's detail view.
 *
 * The state is **one value the ViewModel owns and edits**, not a projection assembled from a flow
 * per moving part. Everything this page discovers for itself is a field of [PostDetailUiState].
 * What comes from outside enters through exactly one collector, [observeStores]: [PostRepository]
 * holds the post in a store shared with the feed and every profile, so a like toggled underneath,
 * or a delete performed on another screen, has to reach this page without it asking.
 *
 * Comments, by contrast, are this ViewModel's own, and are discarded when it is cleared. This
 * avoids the cross-post keying and memory-retention problems a shared comment store would bring.
 *
 * The entity store normally already holds the post. It does not when the page is restored after
 * **process death**, so [loadPost] fetches the post it was given the ID for. That fetch is why an
 * absent post can't simply mean [Content.NotFound]: "not asked yet", "the server says it's gone"
 * and "the request failed" are three different screens, and [PostFetch] tells them apart.
 *
 * Intent arrives as one [PostDetailUiEvent] through [onEvent], which the screen reaches through
 * [PostDetailUiState.eventSink]. [postId] is a runtime argument wired through [Factory] /
 * assisted injection, so every opened post gets its own ViewModel, scoped to its back-stack entry.
 */
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

    /**
     * How the fetch for a post the stores don't hold resolved. Only consulted while the stores
     * can't answer: a post that is there is shown, whatever happened to a request beforehand.
     */
    private sealed interface PostFetch {
        data object Pending : PostFetch

        data object Missing : PostFetch

        data class Failed(
            val error: AppError,
        ) : PostFetch
    }

    private var postFetch: PostFetch = PostFetch.Pending

    // TODO: Wouldn't it be better to initialize as Content.Loading?

    /**
     * The page, as one value this ViewModel owns and edits. Seeded from [content] rather than from
     * [Content.Loading], so a page opened the ordinary way is [Content.Loaded] before the first
     * frame, with no spinner flashed for a post that was never missing.
     */
    private val _uiState = MutableStateFlow(
        PostDetailUiState(
            content = content(),
            composerUser = currentUser,
            eventSink = ::onEvent,
        ),
    )

    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var reportJob: Job? = null

    init {
        observeStores()
        retry()
    }

    fun onEvent(event: PostDetailUiEvent) {
        when (event) {
            PostDetailUiEvent.GoBack -> {
                navigator.goBack()
            }

            is PostDetailUiEvent.OpenProfile -> {
                navigator.goTo(Screen.Profile(event.userId))
            }

            is PostDetailUiEvent.OpenVideo -> {
                navigator.goTo(Screen.FullscreenVideo(event.video))
            }

            is PostDetailUiEvent.OpenAlbum -> {
                navigator.goTo(
                    Screen.AlbumViewer(event.imageUrls, event.initialIndex),
                )
            }

            PostDetailUiEvent.ToggleLike -> {
                toggleLike()
            }

            PostDetailUiEvent.ToggleBookmark -> {
                toggleBookmark()
            }

            PostDetailUiEvent.Delete -> {
                delete()
            }

            is PostDetailUiEvent.Report -> {
                report(event.reason, event.details)
            }

            PostDetailUiEvent.CloseReport -> {
                dropReport(::reportJob, ::setReportSend)
            }

            PostDetailUiEvent.Retry -> {
                retry()
            }

            is PostDetailUiEvent.AddComment -> {
                addComment(event.text)
            }

            is PostDetailUiEvent.ToggleCommentLike -> {
                toggleCommentLike(event.commentId)
            }

            PostDetailUiEvent.LoadMoreComments -> {
                loadMoreComments()
            }

            PostDetailUiEvent.RetryComments -> {
                retryComments()
            }

            PostDetailUiEvent.FailedActionShown -> {
                _uiState.update { it.copy(failedAction = null) }
            }

            PostDetailUiEvent.CommentSent -> {
                setCommentSend(CommentSendState.IDLE)
            }

            PostDetailUiEvent.ScrolledToComment -> {
                mutateCommentThread { it.copy(scrollTo = null) }
            }
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

    private fun observeStores() {
        viewModelScope.launch {
            combine(
                postRepository.entities,
                userRepository.users,
                postRepository.deletedIds,
            ) { _, _, _ -> }.collect { updateContent() }
        }
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

    private fun retry() = launchIfIdle(::loadJob) {
        coroutineScope {
            launch { loadPost() }
            launch { loadComments() }
        }
    }

    /**
     * Fetches the post unless the stores already hold it with its author. The fetch is for the
     * cold start: a page restored after process death, where nothing has asked for the post yet.
     */
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

    /**
     * Deletes the post and pops this screen. Backing out is part of the outcome: once the entity
     * is gone this view has nothing left to show, and the feed underneath has already dropped it.
     */
    private fun delete() = launchReporting(FailedAction.DELETE_POST, ::setFailedAction) {
        // A failed delete throws before the pop, so the page stays put and announces the failure.
        postRepository.delete(postId)
        navigator.goBack()
    }

    /**
     * Reports the post. Unlike [delete] the page stays where it is, and the dialog with it, which
     * is why the outcome goes to [PostDetailUiState.reportSend] and not to a covered snackbar.
     */
    private fun report(reason: ReportReason, details: String) =
        launchReport(::reportJob, ::setReportSend) {
            postRepository.report(postId, reason, details)
        }

    private fun retryComments() {
        viewModelScope.launch { loadComments() }
    }

    private suspend fun loadComments() {
        mutateCommentThread { it.copy(isLoading = true, error = null) }

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

    /**
     * Appends the page after the one loaded last.
     *
     * The cursor is the whole guard: it is null before the first page lands and again once the
     * server says that page was the last, so neither case needs a flag of its own. [loadMoreJob]
     * covers the third — the screen asking again while a page is still on the wire.
     */
    private fun loadMoreComments() = launchIfIdle(::loadMoreJob) {
        val cursor = _uiState.value.commentThread.nextCursor ?: return@launchIfIdle

        catchErrors {
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

    /** Flips the like on a comment before the request goes out, reconciling when the answer lands. */
    private fun toggleCommentLike(commentId: CommentId) = launchCatching {
        val thread = _uiState.value.commentThread
        val before = thread.comments.find { it.id == commentId } ?: return@launchCatching
        val liked = !before.isLiked
        // A later tap has already moved past this request, so its answer is the one to trust.
        val stillOurs = { comment: Comment -> comment.isLiked == liked }

        updateComment(commentId) {
            it.copy(isLiked = liked, likeCount = it.likeCount + if (liked) 1 else -1)
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
            updateComment(commentId, stillOurs) {
                it.copy(isLiked = before.isLiked, likeCount = before.likeCount)
            }
            throw e
        }
    }

    /**
     * Applies [mutate] to [commentId] where it sits in the thread, if it is still there and
     * [predicate] accepts what it currently says. Anything else is left alone.
     */
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
