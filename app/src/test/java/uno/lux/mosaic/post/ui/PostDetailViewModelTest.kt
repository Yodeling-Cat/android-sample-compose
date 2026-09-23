package uno.lux.mosaic.post.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.comment.data.CommentDataSource
import uno.lux.mosaic.comment.data.CommentRepository
import uno.lux.mosaic.comment.data.FakeCommentDataSource
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.post.ui.PostDetailUiState.Content
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import java.net.UnknownHostException
import java.time.Instant

/**
 * The ViewModel is driven the way the screen drives it — one [PostDetailUiEvent] at a time
 * through [PostDetailViewModel.onEvent] — and asserted on the one [PostDetailUiState] it holds.
 *
 * There is no `backgroundScope.launch { uiState.collect {} }` here. The state is a value the
 * ViewModel owns rather than a projection shared while subscribed, so `uiState.value` is the
 * current screen from the moment the ViewModel exists, and a test that forgot to subscribe can
 * no longer read a stale `Loading` and pass for the wrong reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest : ViewModelTest() {

    private val currentUser = User(id = "me", nickname = "Me", handle = "@me")
    private val otherUser = User(id = "u2", nickname = "Other", handle = "@other")

    private val post = Post(
        id = "p1",
        url = testPostUrl("p1"),
        authorId = "u2",
        title = "The Title",
        body = "The body.",
        createdAt = Instant.EPOCH,
        likeCount = 3,
        commentCount = 1,
    )

    private val seedComment = Comment(
        id = "c1",
        author = otherUser,
        createdAt = Instant.EPOCH,
        text = "Nice post",
        likeCount = 2,
    )

    // The real Navigator drives a plain list, so navigation tests assert on the stack directly.
    private val backStack = backStackOf(Screen.Shell, Screen.PostDetail("p1"))
    private val navigator = Navigator().apply { attach(backStack) }

    /** The entity store the last [viewModel] was built on, for driving it from outside. */
    private lateinit var postRepository: PostRepository

    private fun viewModel(
        postId: String = "p1",
        posts: List<Post> = listOf(post),
        users: List<User> = listOf(currentUser, otherUser),
        comments: Map<String, List<Comment>> = mapOf("p1" to listOf(seedComment)),
        commentDataSource: CommentDataSource = FakeCommentDataSource(currentUser, comments),
        postDataSource: FakePostDataSource = FakePostDataSource(),
    ): PostDetailViewModel {
        val userRepo = UserRepository(FakeUserDataSource())
        userRepo.ingest(users)
        val postRepo = PostRepository(postDataSource, userRepo)
        postRepo.ingest(posts)
        postRepository = postRepo
        return PostDetailViewModel(
            postRepository = postRepo,
            commentRepository = CommentRepository(commentDataSource),
            userRepository = userRepo,
            navigator = navigator,
            currentUser = currentUser,
            postId = postId,
        )
    }

    /** A comment source on p1, typed as the fake so a test can reach its like bookkeeping. */
    private fun commentSource(thread: List<Comment> = listOf(seedComment)) =
        FakeCommentDataSource(currentUser, mapOf("p1" to thread))

    /** A thread of [size] comments, ids `c1`..`cN` in the order the server would send them. */
    private fun thread(size: Int) = List(size) { index ->
        seedComment.copy(id = "c${index + 1}", text = "Comment ${index + 1}")
    }

    /** A comment source on p1 that hands its thread out [pageSize] comments at a time. */
    private fun pagedSource(thread: List<Comment>, pageSize: Int) =
        FakeCommentDataSource(currentUser, mapOf("p1" to thread), pageSize = pageSize)

    @Test
    fun `a post by someone else is not marked own`() = runTest {
        assertFalse(viewModel().loaded.isOwn)
    }

    @Test
    fun `a post by the signed-in user is marked own`() = runTest {
        assertTrue(viewModel(posts = listOf(post.copy(authorId = "me"))).loaded.isOwn)
    }

    @Test
    fun `Delete removes the post and pops the detail page`() = runTest {
        val viewModel = viewModel(posts = listOf(post.copy(authorId = "me")))

        viewModel.onEvent(PostDetailUiEvent.Delete)

        assertEquals(Content.NotFound, viewModel.uiState.value.content)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    // A refused delete must not pop the page: the post is still there, and leaving would make
    // the failure read as success. The announcement is the only thing the tap produces.
    @Test
    fun `a failed delete keeps the page open and names the action for the screen`() = runTest {
        val dataSource = FakePostDataSource().apply { deleteError = UnknownHostException("offline") }
        val viewModel = viewModel(
            posts = listOf(post.copy(authorId = "me")),
            postDataSource = dataSource,
        )

        viewModel.onEvent(PostDetailUiEvent.Delete)

        assertTrue(viewModel.uiState.value.content is Content.Loaded)
        assertEquals(listOf(Screen.Shell, Screen.PostDetail("p1")), backStack.screens())
        assertEquals(FailedAction.DELETE_POST, viewModel.uiState.value.failedAction)
    }

    // Announced once and then spent, the same lifetime the feed gives its refresh error: what is
    // left in the state is what a rotation would replay.
    @Test
    fun `FailedActionShown clears the announcement`() = runTest {
        val dataSource = FakePostDataSource().apply { deleteError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)
        viewModel.onEvent(PostDetailUiEvent.Delete)

        viewModel.onEvent(PostDetailUiEvent.FailedActionShown)

        assertNull(viewModel.uiState.value.failedAction)
    }

    // This page can sit *under* the screen that deletes its post — open a post, visit the
    // author's profile, delete it there, come back. Absence from the store is ambiguous
    // (never loaded? gone?), so the deletion has to reach this page as an answer, not as an
    // emptied store that reads as "not loaded yet" and spins forever.
    @Test
    fun `a post deleted from another screen reads as gone, not loading`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.content is Content.Loaded)

        postRepository.delete("p1")

        assertEquals(Content.NotFound, vm.uiState.value.content)
    }

    // A report is about the post, not on it: unlike a deletion it changes nothing the page
    // shows, and leaves the reporter reading what they reported.
    @Test
    fun `Report reports the post and keeps the page open`() = runTest {
        val dataSource = FakePostDataSource()
        val viewModel = viewModel(postDataSource = dataSource)

        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, "Second time this week"))

        assertEquals(
            listOf(FakePostDataSource.Report("p1", ReportReason.HARASSMENT, "Second time this week")),
            dataSource.reports,
        )
        assertTrue(viewModel.uiState.value.content is Content.Loaded)
        assertEquals(listOf(Screen.Shell, Screen.PostDetail("p1")), backStack.screens())
    }

    // The dialog is the whole point of these states: it stays up for the send, disables Send off
    // SENDING, and closes on SENT — which is the moment the thanks stops being a guess.
    @Test
    fun `a report is SENDING while it is out and SENT once the server has it`() = runTest {
        val dataSource = FakePostDataSource()
        val viewModel = viewModel(postDataSource = dataSource)
        var whileOut: ReportSendState? = null
        dataSource.whileInFlight = { whileOut = viewModel.uiState.value.reportSend }

        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))

        assertEquals(ReportSendState.SENDING, whileOut)
        assertEquals(ReportSendState.SENT, viewModel.uiState.value.reportSend)
    }

    // A report the server refused has to fail *in* the dialog: the reporter is still reading it,
    // and a snackbar would be announced behind its scrim.
    @Test
    fun `a failed report fails in the dialog rather than in a snackbar`() = runTest {
        val dataSource = FakePostDataSource().apply { reportError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)

        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))

        assertEquals(ReportSendState.FAILED, viewModel.uiState.value.reportSend)
        assertNull(viewModel.uiState.value.failedAction)
    }

    // What the user picked is still in the dialog after a failure, so Send is one tap away —
    // and the second attempt must not be turned away as a duplicate of the first.
    @Test
    fun `a report can be sent again after it failed`() = runTest {
        val dataSource = FakePostDataSource().apply { reportError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)
        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))
        dataSource.reportError = null

        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))

        assertEquals(ReportSendState.SENT, viewModel.uiState.value.reportSend)
        assertEquals(
            listOf(FakePostDataSource.Report("p1", ReportReason.HARASSMENT, "")),
            dataSource.reports,
        )
    }

    @Test
    fun `CloseReport drops the outcome the dialog has acted on`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))

        viewModel.onEvent(PostDetailUiEvent.CloseReport)

        assertEquals(ReportSendState.IDLE, viewModel.uiState.value.reportSend)
    }

    // Dismissing mid-send abandons the report along with the dialog. Without the cancellation
    // its answer would still land in the state, and the *next* dialog would open on the outcome
    // of a report the user walked away from — thanking them for it, or failing at them.
    @Test
    fun `a report dismissed while it is out cannot settle behind the dialog`() = runTest {
        val dataSource = FakePostDataSource()
        val viewModel = viewModel(postDataSource = dataSource)
        val held = CompletableDeferred<Unit>()
        dataSource.whileInFlight = { held.await() }

        viewModel.onEvent(PostDetailUiEvent.Report(ReportReason.HARASSMENT, ""))
        viewModel.onEvent(PostDetailUiEvent.CloseReport)
        held.complete(Unit)
        advanceUntilIdle()

        assertEquals(ReportSendState.IDLE, viewModel.uiState.value.reportSend)
        assertEquals(emptyList<FakePostDataSource.Report>(), dataSource.reports)
    }

    @Test
    fun `GoBack pops the detail page`() {
        viewModel().onEvent(PostDetailUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `OpenProfile pushes the author's profile page`() {
        viewModel().onEvent(PostDetailUiEvent.OpenProfile("u2"))

        assertEquals(Screen.Profile("u2"), backStack.last().screen)
    }

    @Test
    fun `OpenAlbum pushes the album viewer at the tapped image`() {
        val images = listOf("https://example.test/1.jpg")

        viewModel().onEvent(PostDetailUiEvent.OpenAlbum(images, initialIndex = 0))

        assertEquals(Screen.AlbumViewer(images, initialIndex = 0), backStack.last().screen)
    }

    // The state is seeded from the stores rather than from Loading, so a page opened the ordinary
    // way is showing its post before the first frame — no spinner for something never missing.
    @Test
    fun `uiState is Loaded from the first read, with nothing collecting`() {
        val vm = viewModel()

        assertEquals(post, vm.loaded.post)
        assertEquals(otherUser, vm.loaded.author)
    }

    @Test
    fun `uiState carries the post, its author and its comments`() = runTest {
        val vm = viewModel()

        assertEquals(post, vm.loaded.post)
        assertEquals(otherUser, vm.loaded.author)
        assertEquals(listOf(seedComment), vm.comments)
        assertFalse(vm.uiState.value.commentThread.isLoading)
    }

    @Test
    fun `comments are marked loading until they resolve`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(commentDataSource = commentSource().apply { whileLoading = { gate.await() } })
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commentThread.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.commentThread.isLoading)
        assertEquals(listOf(seedComment), vm.comments)
    }

    // The post and the thread are fetched in parallel on a cold start, and the thread is a field
    // *beside* the content rather than inside its Loaded case precisely so the one that lands
    // first is not thrown away for having nowhere to go. Here the post never arrives at all, and
    // the comments that did are still held.
    @Test
    fun `comments loaded while there is no post are kept`() = runTest {
        val dataSource = FakePostDataSource().apply { fetchError = UnknownHostException("offline") }
        val vm = viewModel(posts = emptyList(), users = emptyList(), postDataSource = dataSource)
        advanceUntilIdle()

        assertEquals(Content.Error(AppError.NoConnection), vm.uiState.value.content)
        assertEquals(listOf(seedComment), vm.comments)
    }

    //
    // The stores are empty when the app is restarted straight onto this page, so the ViewModel
    // has to fetch the post its back-stack key names. Whether that fetch is still running, came
    // back empty, or failed outright are three different screens.

    @Test
    fun `a page restored with empty stores fetches the post it was given the id for`() = runTest {
        val dataSource = FakePostDataSource().apply {
            fetchable["p1"] = PostWithUsers(post, listOf(otherUser))
        }
        val vm = viewModel(posts = emptyList(), users = emptyList(), postDataSource = dataSource)
        advanceUntilIdle()

        assertEquals(post, vm.loaded.post)
        // The author rides along with the post, so the header resolves without a second request.
        assertEquals(otherUser, vm.loaded.author)
    }

    @Test
    fun `a post the stores already hold is not re-fetched`() = runTest {
        val dataSource = FakePostDataSource()
        val vm = viewModel(postDataSource = dataSource)
        advanceUntilIdle()

        assertEquals(emptyList<PostId>(), dataSource.fetchedPostIds)
        assertTrue(vm.uiState.value.content is Content.Loaded)
    }

    @Test
    fun `uiState stays Loading while the fetch is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(posts = emptyList(), users = emptyList())

        assertEquals(Content.Loading, vm.uiState.value.content)
    }

    @Test
    fun `uiState is NotFound when the server no longer has the post`() = runTest {
        val vm = viewModel(postId = "missing")
        advanceUntilIdle()

        assertEquals(Content.NotFound, vm.uiState.value.content)
    }

    // A failed request says nothing about whether the post exists, so it must not read as "gone".
    @Test
    fun `uiState is a retryable Error when the fetch fails`() = runTest {
        val dataSource = FakePostDataSource().apply { fetchError = UnknownHostException("offline") }
        val vm = viewModel(posts = emptyList(), users = emptyList(), postDataSource = dataSource)
        advanceUntilIdle()

        assertEquals(Content.Error(AppError.NoConnection), vm.uiState.value.content)
    }

    @Test
    fun `Retry after a failed fetch loads the post`() = runTest {
        val dataSource = FakePostDataSource().apply { fetchError = UnknownHostException("offline") }
        val vm = viewModel(posts = emptyList(), users = emptyList(), postDataSource = dataSource)
        advanceUntilIdle()

        dataSource.fetchError = null
        dataSource.fetchable["p1"] = PostWithUsers(post, listOf(otherUser))
        vm.onEvent(PostDetailUiEvent.Retry)
        advanceUntilIdle()

        assertEquals(post, vm.loaded.post)
    }

    @Test
    fun `composerUser is the current user`() {
        assertEquals(currentUser, viewModel().uiState.value.composerUser)
    }

    @Test
    fun `ToggleLike likes the post through the shared entity store`() = runTest {
        // The count in the answer is the server's, so the fake is told what it started from.
        val vm = viewModel(
            postDataSource = FakePostDataSource().apply { likeCounts["p1"] = post.likeCount },
        )

        vm.onEvent(PostDetailUiEvent.ToggleLike)

        assertTrue(vm.loaded.post.isLiked)
        assertEquals(4, vm.loaded.post.likeCount)
    }

    @Test
    fun `ToggleBookmark bookmarks the post through the shared entity store`() = runTest {
        val vm = viewModel()

        vm.onEvent(PostDetailUiEvent.ToggleBookmark)

        assertTrue(vm.loaded.post.isBookmarked)
    }

    @Test
    fun `AddComment prepends a new comment to the thread`() = runTest {
        val vm = viewModel()

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        val comments = vm.comments
        assertEquals(2, comments.size)
        assertEquals("Hello!", comments.first().text)
        assertEquals(currentUser, comments.first().author)
    }

    // The comment lands in this ViewModel's own list, but the count under the post is a field on
    // the entity — so it has to go through the store, or the header here, the feed card and the
    // profile all keep showing the number the post had before the user commented on it.
    @Test
    fun `AddComment bumps the post's comment count in the shared store`() = runTest {
        val vm = viewModel()

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        assertEquals(2, vm.loaded.post.commentCount)
        assertEquals(2, postRepository.entities.value["p1"]!!.commentCount)
    }

    // The thread is this ViewModel's own and the count arrives from the shared store, and the new
    // comment appearing while the count under the post stays put is what a stale build looks like.
    // Driven on a real dispatcher rather than an unconfined one, so an emission that never reached
    // the state would settle wrong instead of being papered over.
    @Test
    fun `the thread and the count settle together on the same state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("the thread carries the new comment", 2, state.commentThread.comments.size)
        assertEquals(
            "the header and action row count the post",
            2,
            (state.content as Content.Loaded).post.commentCount,
        )
    }

    // The count follows the comment: a post that never landed must not inflate it.
    @Test
    fun `a failed AddComment leaves the comment count alone`() = runTest {
        val commentDataSource = commentSource().apply { addError = UnknownHostException("offline") }
        val vm = viewModel(commentDataSource = commentDataSource)

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        assertEquals(1, vm.loaded.post.commentCount)
        assertEquals(listOf(seedComment), vm.comments)
    }

    // The composer holds the text until the server has the comment, so SENT is the signal that
    // says it may finally let go of it.
    @Test
    fun `a comment the server takes ends in SENT`() = runTest {
        val vm = viewModel()

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        assertEquals(CommentSendState.SENT, vm.uiState.value.commentSend)
    }

    // The field and the send button are disabled off this state. Without it a second tap would
    // post the comment twice, since nothing else stops one.
    @Test
    fun `the composer reads as sending while the comment is on the wire`() = runTest {
        val answered = CompletableDeferred<Unit>()
        val source = commentSource().apply { whileAdding = { answered.await() } }
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))
        advanceUntilIdle()

        assertEquals(CommentSendState.SENDING, vm.uiState.value.commentSend)

        answered.complete(Unit)
        advanceUntilIdle()

        assertEquals(CommentSendState.SENT, vm.uiState.value.commentSend)
    }

    // The whole point of not clearing up front: a refused comment leaves the composer editable,
    // with what the user typed still in it, and says so.
    @Test
    fun `a failed send returns the composer to idle and names the action`() = runTest {
        val source = commentSource().apply { addError = UnknownHostException("offline") }
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        assertEquals(CommentSendState.IDLE, vm.uiState.value.commentSend)
        assertEquals(FailedAction.SEND_COMMENT, vm.uiState.value.failedAction)
    }

    // Spent once the composer has given the text up, so a rotation cannot clear a box the user
    // has already started refilling.
    @Test
    fun `CommentSent spends the send signal`() = runTest {
        val vm = viewModel()
        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        vm.onEvent(PostDetailUiEvent.CommentSent)

        assertEquals(CommentSendState.IDLE, vm.uiState.value.commentSend)
    }

    // The thread hangs below the whole post, so a comment sent from a long page lands off screen
    // unless the screen is told to go to it.
    @Test
    fun `AddComment names the new comment as the one to scroll to`() = runTest {
        val vm = viewModel()

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        val thread = vm.uiState.value.commentThread
        assertEquals(thread.comments.first().id, thread.scrollTo)
    }

    // Spent once acted on: a configuration change must not yank the list back a second time.
    @Test
    fun `ScrolledToComment spends the scroll signal`() = runTest {
        val vm = viewModel()
        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        vm.onEvent(PostDetailUiEvent.ScrolledToComment)

        assertNull(vm.uiState.value.commentThread.scrollTo)
    }

    // Nothing was posted, so there is nothing to go to.
    @Test
    fun `a failed AddComment names nothing to scroll to`() = runTest {
        val commentDataSource = commentSource().apply { addError = UnknownHostException("offline") }
        val vm = viewModel(commentDataSource = commentDataSource)

        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        assertNull(vm.uiState.value.commentThread.scrollTo)
    }

    // A reload starts the thread over, so a scroll owed into the window it replaced has nothing
    // left to aim at — and the screen would otherwise jump to whatever now sits at that index.
    @Test
    fun `RetryComments drops a pending scroll`() = runTest {
        val vm = viewModel()
        vm.onEvent(PostDetailUiEvent.AddComment("Hello!"))

        vm.onEvent(PostDetailUiEvent.RetryComments)

        assertNull(vm.uiState.value.commentThread.scrollTo)
    }

    @Test
    fun `ToggleCommentLike likes a comment in the thread`() = runTest {
        // The count in the answer is the server's, so the fake is told what it started from.
        val vm = viewModel(commentDataSource = commentSource().apply { likeCounts["c1"] = 2 })

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))

        val liked = vm.comments.single()
        assertTrue(liked.isLiked)
        assertEquals(3, liked.likeCount)
    }

    @Test
    fun `ToggleCommentLike asks for the state opposite the comment's own`() = runTest {
        val source = commentSource(listOf(seedComment.copy(isLiked = true)))
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))

        assertEquals(listOf("c1" to false), source.likeRequests)
    }

    // The heart under a comment fills on the tap for the same reason the post's does — waiting out
    // a round trip reads as broken. The request is held open on [answered] so the in-flight state
    // can be read, and the fake's server is seeded to disagree with the count the thread was
    // loaded with, which is what tells the optimistic value (3) from the confirmed one (11).
    @Test
    fun `ToggleCommentLike fills the heart before the request answers`() = runTest {
        val answered = CompletableDeferred<Unit>()
        val source = commentSource().apply {
            likeCounts["c1"] = 10
            whileInFlight = { answered.await() }
        }
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))
        advanceUntilIdle()

        val inFlight = vm.comments.single()
        assertTrue(inFlight.isLiked)
        assertEquals(3, inFlight.likeCount)

        answered.complete(Unit)
        advanceUntilIdle()

        // And once it lands, the server's count replaces the guess.
        assertEquals(11, vm.comments.single().likeCount)
    }

    @Test
    fun `a failed comment like puts the heart back`() = runTest {
        val source = commentSource().apply {
            likeCounts["c1"] = 2
            setLikeError = UnknownHostException("offline")
        }
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))

        val comment = vm.comments.single()
        assertFalse(comment.isLiked)
        assertEquals(2, comment.likeCount)
    }

    // A comment like writes only the two fields it owns, so a thread reloaded while the request
    // was in flight keeps the text it brought back rather than the copy the tap was made against.
    @Test
    fun `a comment like answer leaves the rest of the comment alone`() = runTest {
        val source = commentSource().apply { likeCounts["c1"] = 2 }
        val vm = viewModel(commentDataSource = source)
        source.whileInFlight = {
            source.comments = mapOf("p1" to listOf(seedComment.copy(text = "Edited", isLiked = true)))
            vm.onEvent(PostDetailUiEvent.RetryComments)
        }

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))

        val comment = vm.comments.single()
        assertEquals("Edited", comment.text)
        assertTrue(comment.isLiked)
    }

    // Two taps in flight: the second is what the user last asked for, so the first request's
    // answer — which describes a state nobody is waiting for — must not overwrite it.
    @Test
    fun `a comment like answer that a newer tap has moved past is dropped`() = runTest {
        val source = commentSource().apply { likeCounts["c1"] = 2 }
        val vm = viewModel(commentDataSource = source)
        source.whileInFlight = {
            // Only the first request re-enters; the second must not recurse.
            source.whileInFlight = null
            vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))
        }

        vm.onEvent(PostDetailUiEvent.ToggleCommentLike("c1"))

        assertEquals(listOf("c1" to true, "c1" to false), source.likeRequests)
        assertFalse(vm.comments.single().isLiked)
    }

    //
    // A thread arrives a window at a time, so opening a post with hundreds of comments costs the
    // same first request as opening one with three.

    @Test
    fun `only the first page of the thread is loaded up front`() = runTest {
        val full = thread(5)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)

        val thread = vm.uiState.value.commentThread
        assertEquals(full.take(2), thread.comments)
        assertFalse(thread.endReached)
        assertEquals(listOf("p1" to null), source.loadRequests)
    }

    @Test
    fun `LoadMoreComments appends the page after the one loaded last`() = runTest {
        val full = thread(5)
        val vm = viewModel(commentDataSource = pagedSource(full, pageSize = 2))

        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        val thread = vm.uiState.value.commentThread
        assertEquals(full.take(4), thread.comments)
        assertFalse(thread.endReached)
    }

    // The end of the thread is the server's to declare, and the screen stops asking on it — the
    // spinner under the last comment has to become nothing rather than spin forever.
    @Test
    fun `the thread ends when the last page lands, and nothing more is asked for`() = runTest {
        val full = thread(4)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.LoadMoreComments)
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        val thread = vm.uiState.value.commentThread
        assertEquals(full, thread.comments)
        assertTrue(thread.endReached)
        assertEquals(listOf("p1" to null, "p1" to "c2"), source.loadRequests)
    }

    // The list is near its end while the first page is still on the wire, so the screen asks
    // early. There is no cursor to ask with yet, and a request without one would re-fetch the
    // page already coming.
    @Test
    fun `LoadMoreComments waits for the first page to hand back a cursor`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = pagedSource(thread(5), pageSize = 2).apply { whileLoading = { gate.await() } }
        val vm = viewModel(commentDataSource = source)

        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        assertEquals(listOf("p1" to null), source.loadRequests)

        gate.complete(Unit)
    }

    // A page that failed leaves the thread as it was: the comments already read stay on screen.
    @Test
    fun `a failed page leaves the loaded thread alone`() = runTest {
        val full = thread(5)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)

        source.loadError = UnknownHostException("offline")
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        val thread = vm.uiState.value.commentThread
        assertEquals(full.take(2), thread.comments)
        assertFalse(thread.endReached)
    }

    // The failure has to reach the footer, or it would keep spinning over a page nobody is fetching.
    @Test
    fun `a failed page is carried on the thread for the footer to offer a retry`() = runTest {
        val source = pagedSource(thread(5), pageSize = 2)
        val vm = viewModel(commentDataSource = source)

        source.loadError = UnknownHostException("offline")
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        assertTrue(vm.uiState.value.commentThread.loadMoreFailed)
    }

    @Test
    fun `asking for the page again clears the failure and appends it`() = runTest {
        val full = thread(5)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)
        source.loadError = UnknownHostException("offline")
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)
        source.loadError = null

        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        val thread = vm.uiState.value.commentThread
        assertFalse(thread.loadMoreFailed)
        assertEquals(full.take(4), thread.comments)
    }

    @Test
    fun `RetryComments clears a failed page, since it starts the thread over`() = runTest {
        val source = pagedSource(thread(5), pageSize = 2)
        val vm = viewModel(commentDataSource = source)
        source.loadError = UnknownHostException("offline")
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)
        source.loadError = null

        vm.onEvent(PostDetailUiEvent.RetryComments)

        assertFalse(vm.uiState.value.commentThread.loadMoreFailed)
    }

    // A retry is a fresh thread, not a resumed one: the cursor it was holding describes a window
    // into a thread that may have moved since.
    @Test
    fun `RetryComments starts the thread over from the first page`() = runTest {
        val full = thread(5)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)
        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        vm.onEvent(PostDetailUiEvent.RetryComments)

        assertEquals(full.take(2), vm.comments)
        assertEquals(listOf("p1" to null, "p1" to "c2", "p1" to null), source.loadRequests)
    }

    // A reload that lands mid-flight has started the thread over, so the page still on the wire
    // describes a window that no longer follows what is on screen — appending it would show the
    // reader comments from two different threads, and duplicate keys in the list.
    @Test
    fun `a page a reload has moved past is not appended`() = runTest {
        val full = thread(5)
        val source = pagedSource(full, pageSize = 2)
        val vm = viewModel(commentDataSource = source)
        val newest = seedComment.copy(id = "c0", text = "Posted since")
        source.whileLoading = {
            // Only the load-more re-enters; the reload it triggers must not recurse.
            source.whileLoading = null
            source.comments = mapOf("p1" to (listOf(newest) + full))
            vm.onEvent(PostDetailUiEvent.RetryComments)
            // Hands the thread over so the reload finishes here, while this page is still on the
            // wire — which is the whole of what this test is about.
            yield()
        }

        vm.onEvent(PostDetailUiEvent.LoadMoreComments)

        assertEquals(listOf(newest, full[0]), vm.comments)
    }

    @Test
    fun `state updates emit to subscribers after AddComment`() = runTest {
        val vm = viewModel(comments = emptyMap())
        val collected = mutableListOf<PostDetailUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { collected.add(it) }
        }

        vm.onEvent(PostDetailUiEvent.AddComment("First comment"))

        val comments = collected.last().commentThread.comments
        assertEquals(1, comments.size)
        assertEquals("First comment", comments.first().text)
    }

    @Test
    fun `comment state is scoped to this post's id`() = runTest {
        val vm = viewModel(comments = mapOf("p1" to listOf(seedComment)))

        vm.onEvent(PostDetailUiEvent.AddComment("Only for p1"))

        assertEquals(2, vm.comments.size)
    }
}

/** The post the page is showing, for the tests whose subject is the post rather than the load. */
private val PostDetailViewModel.loaded: Content.Loaded
    get() = uiState.value.content as Content.Loaded

/** The stretch of the thread the page is holding. */
private val PostDetailViewModel.comments: List<Comment>
    get() = uiState.value.commentThread.comments
