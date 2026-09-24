package uno.lux.mosaic.profile.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.ui.PostReport
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.profile.data.FakeProfileDataSource
import uno.lux.mosaic.profile.data.PostsPage
import uno.lux.mosaic.profile.data.ProfileRefreshData
import uno.lux.mosaic.profile.data.ProfileRepository
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import java.net.UnknownHostException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest : ViewModelTest() {

    private val ada = User(id = "u1", nickname = "Ada", handle = "@ada")
    private val grace = User(id = "u2", nickname = "Grace", handle = "@grace")
    private val post = Post(
        id = "p1",
        url = testPostUrl("p1"),
        authorId = "u1",
        title = "Title",
        body = "Body",
        createdAt = Instant.EPOCH,
        likeCount = 10,
        commentCount = 2,
    )

    // The real Navigator drives a plain list, so navigation tests assert on the stack directly.
    private val backStack = backStackOf(Screen.Shell, Screen.Profile("u1"))
    private val navigator = Navigator().apply { attach(backStack) }

    private val savedPost = Post(
        id = "p2",
        url = testPostUrl("p2"),
        authorId = "u2",
        title = "Saved",
        body = "Body",
        createdAt = Instant.EPOCH,
        likeCount = 3,
        commentCount = 0,
        isBookmarked = true,
    )

    private val likedPost = Post(
        id = "p3",
        url = testPostUrl("p3"),
        authorId = "u2",
        title = "Liked",
        body = "Body",
        createdAt = Instant.EPOCH,
        likeCount = 7,
        commentCount = 0,
        isLiked = true,
    )

    private lateinit var profileDataSource: FakeProfileDataSource

    private fun viewModel(
        userId: UserId = "u1",
        currentUserId: UserId = "u1",
        bookmarks: List<Post> = listOf(savedPost),
        likes: List<Post> = listOf(likedPost),
        postAuthors: List<User> = listOf(ada),
        postsHaveMore: Boolean = false,
        userDataSource: UserDataSource = FakeUserDataSource(mapOf("u1" to ada, "u2" to grace)),
        postDataSource: FakePostDataSource = FakePostDataSource(),
    ): ProfileViewModel {
        val userRepo = UserRepository(userDataSource)
        val postRepo = PostRepository(postDataSource, userRepo)
        profileDataSource = FakeProfileDataSource(
            refreshData = mapOf(
                "u1" to ProfileRefreshData(
                    postsCount = 1,
                    page = PostsPage(
                        posts = listOf(post),
                        users = postAuthors,
                        cursor = if (postsHaveMore) "c2" else null,
                        hasMore = postsHaveMore,
                    ),
                ),
                "u2" to ProfileRefreshData(
                    postsCount = 0,
                    page = PostsPage(emptyList(), emptyList(), null, false),
                ),
            ),
            bookmarks = mapOf(
                "u1" to mapOf(null to PostsPage(bookmarks, listOf(grace), null, false)),
            ),
            likes = mapOf(
                "u1" to mapOf(null to PostsPage(likes, listOf(grace), null, false)),
            ),
        )
        val profileRepo = ProfileRepository(
            profileDataSource,
            postRepo,
            userRepo,
            currentUserId,
        )
        return ProfileViewModel(
            profileRepository = profileRepo,
            postRepository = postRepo,
            userRepository = userRepo,
            navigator = navigator,
            currentUserId = currentUserId,
            userId = userId,
        )
    }

    @Test
    fun `Delete drops the post from the profile`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.Delete("p1"))

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertTrue(loaded.data.posts.isEmpty())
    }

    // Someone else's profile is where a post is most likely to be reported, and reporting one
    // must leave the profile showing exactly what it showed.
    @Test
    fun `a report sends for the post it was opened on and leaves the profile as it was`() = runTest {
        val dataSource = FakePostDataSource()
        val viewModel = viewModel(postDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val before = (viewModel.uiState.value as ProfileUiState.Loaded).data.posts

        viewModel.onEvent(ProfileUiEvent.OpenReport("p1"))
        viewModel.onEvent(ProfileUiEvent.SendReport(ReportReason.MISINFORMATION, "None of this is true"))

        assertEquals(
            listOf(
                FakePostDataSource.Report("p1", ReportReason.MISINFORMATION, "None of this is true"),
            ),
            dataSource.reports,
        )
        assertEquals(before, (viewModel.uiState.value as ProfileUiState.Loaded).data.posts)
        assertEquals(PostReport.Sent, viewModel.report.value)
    }

    @Test
    fun `a failed report fails in the dialog, not as an announcement`() = runTest {
        val dataSource = FakePostDataSource().apply { reportError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)

        viewModel.onEvent(ProfileUiEvent.OpenReport("p1"))
        viewModel.onEvent(ProfileUiEvent.SendReport(ReportReason.MISINFORMATION, ""))

        assertEquals(PostReport.Open("p1", ReportSendState.FAILED), viewModel.report.value)
        assertNull(viewModel.failedAction.value)
    }

    @Test
    fun `GoBack pops the profile page`() {
        viewModel().onEvent(ProfileUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `OpenEditProfile pushes the profile editor`() {
        viewModel().onEvent(ProfileUiEvent.OpenEditProfile)

        assertEquals(Screen.EditProfile, backStack.last().screen)
    }

    @Test
    fun `OpenEditProfile does not stack a second editor`() {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileUiEvent.OpenEditProfile)
        viewModel.onEvent(ProfileUiEvent.OpenEditProfile)

        assertEquals(listOf(Screen.Shell, Screen.Profile("u1"), Screen.EditProfile), backStack.screens())
    }

    @Test
    fun `OpenPost pushes the post's detail page`() {
        viewModel().onEvent(ProfileUiEvent.OpenPost("p1"))

        assertEquals(Screen.PostDetail("p1"), backStack.last().screen)
    }

    @Test
    fun `OpenAvatar pushes a single-image album viewer`() {
        viewModel().onEvent(ProfileUiEvent.OpenAvatar("https://example.test/avatar.jpg"))

        assertEquals(
            Screen.AlbumViewer(listOf("https://example.test/avatar.jpg"), initialIndex = 0),
            backStack.last().screen,
        )
    }

    @Test
    fun `uiState is Loading until something collects it`() {
        assertEquals(ProfileUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `uiState exposes the loaded profile once collected`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(ada, loaded.data.user)
        assertEquals(listOf(post), loaded.data.posts)
        assertEquals(1, loaded.data.profile.postsCount)
    }

    @Test
    fun `uiState marks the signed-in user's own profile as current`() = runTest {
        val viewModel = viewModel(userId = "u1", currentUserId = "u1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).isCurrentUser)
    }

    @Test
    fun `uiState marks another user's profile as not current`() = runTest {
        val viewModel = viewModel(userId = "u2", currentUserId = "u1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).isCurrentUser)
    }

    // The same three-state distinction the post detail page makes. A profile restored after
    // process death starts with empty stores, so "the store has no such user" says nothing until
    // the fetch has actually run — reading it as NotFound flashes "profile not found" over the
    // whole of every cold start. Nothing has landed here: the user fetch is held, and the posts
    // page is made to carry no author so it cannot stand in for it.
    @Test
    fun `uiState is Loading while the cold-start fetch is still in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(
            postAuthors = emptyList(),
            userDataSource = GatedUserDataSource(gate, ada),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(ProfileUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProfileUiState.Loaded)
    }

    // What the sideload buys: the profile's own posts arrive with their author, so the page
    // draws off that page alone rather than waiting on GET /users/:id to come back too.
    @Test
    fun `the author sideloaded with the posts renders the profile before the user fetch lands`() =
        runTest {
            val neverAnswers = CompletableDeferred<Unit>()
            val viewModel = viewModel(userDataSource = GatedUserDataSource(neverAnswers, ada))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            advanceUntilIdle()

            val loaded = viewModel.uiState.value as ProfileUiState.Loaded
            assertEquals(ada, loaded.data.user)
            assertEquals(listOf("p1"), loaded.data.posts.map { it.id })
        }

    @Test
    fun `uiState is NotFound for an unknown user`() = runTest {
        val viewModel = viewModel(userId = "nobody")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(ProfileUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `Retry after a failed load reads Loading until the fetch answers, not NotFound`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(
            userId = "nobody",
            userDataSource = GatedUserDataSource(gate, ada, failuresBeforeGate = 1),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ProfileUiState.Error)

        viewModel.onEvent(ProfileUiEvent.Retry)
        advanceUntilIdle()

        assertEquals(ProfileUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ProfileUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `ToggleLike likes the post through the shared entity store`() = runTest {
        // The count in the answer is the server's, so the fake is told what it started from.
        val viewModel = viewModel(
            postDataSource = FakePostDataSource().apply { likeCounts["p1"] = post.likeCount },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.ToggleLike("p1"))

        val liked = (viewModel.uiState.value as ProfileUiState.Loaded).data.posts.single()
        assertTrue(liked.isLiked)
        assertEquals(11, liked.likeCount)
    }

    @Test
    fun `ToggleBookmark bookmarks the post through the shared entity store`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.ToggleBookmark("p1"))

        val bookmarked = (viewModel.uiState.value as ProfileUiState.Loaded).data.posts.single()
        assertTrue(bookmarked.isBookmarked)
    }

    @Test
    fun `bookmarks are absent until the Saved tab is shown`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertNull((viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks)
        assertTrue(profileDataSource.bookmarkCalls.isEmpty())
    }

    @Test
    fun `SavedTabShown loads the saved posts with their authors`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        val saved = (viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks!!
        val card = saved.posts.single()
        assertEquals("p2", card.post.id)
        // A saved post is usually someone else's, so the card carries that author, not the profile's.
        assertEquals(grace, card.author)
        assertFalse(card.isOwn)
        assertTrue(saved.endReached)
    }

    @Test
    fun `a Saved tab whose first load fails says so, rather than pending forever`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        profileDataSource.offline = true

        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        val data = (viewModel.uiState.value as ProfileUiState.Loaded).data
        assertNull(data.bookmarks)
        assertTrue(data.bookmarksLoadFailed)
    }

    @Test
    fun `retrying the Saved tab clears the failure and loads it`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        profileDataSource.offline = true
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)
        profileDataSource.offline = false

        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        val data = (viewModel.uiState.value as ProfileUiState.Loaded).data
        assertFalse(data.bookmarksLoadFailed)
        assertEquals(listOf("p2"), data.bookmarks!!.posts.map { it.post.id })
    }

    @Test
    fun `a failed posts load-more is carried for the footer to offer a retry`() = runTest {
        val viewModel = viewModel(postsHaveMore = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        profileDataSource.offline = true

        viewModel.onEvent(ProfileUiEvent.LoadMorePosts)

        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).data.postsLoadMoreFailed)
    }

    @Test
    fun `a refresh clears a posts load-more failure, since it restarts the list`() = runTest {
        val viewModel = viewModel(postsHaveMore = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        profileDataSource.offline = true
        viewModel.onEvent(ProfileUiEvent.LoadMorePosts)
        profileDataSource.offline = false

        viewModel.onEvent(ProfileUiEvent.Refresh)

        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).data.postsLoadMoreFailed)
    }

    @Test
    fun `SavedTabShown fetches only once across repeat visits`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.SavedTabShown)
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        assertEquals(1, profileDataSource.bookmarkCalls.size)
    }

    @Test
    fun `an empty Saved tab is loaded, not pending`() = runTest {
        val viewModel = viewModel(bookmarks = emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        val saved = (viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks
        assertEquals(emptyList<Any>(), saved?.posts)
    }

    // The saved posts resolve through the shared entity store, so liking one from the Saved tab
    // is reflected on its card without a re-fetch. (Clearing the *bookmark* instead removes the
    // row outright — that is the tab's defining flag, asserted further down.)
    @Test
    fun `liking a saved post updates it in place`() = runTest {
        val viewModel = viewModel(
            postDataSource = FakePostDataSource().apply { likeCounts["p2"] = savedPost.likeCount },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        viewModel.onEvent(ProfileUiEvent.ToggleLike("p2"))

        val card = (viewModel.uiState.value as ProfileUiState.Loaded)
            .data.bookmarks!!
            .posts
            .single()
        assertTrue(card.post.isLiked)
        assertEquals(4, card.post.likeCount)
    }

    @Test
    fun `Refresh leaves an unopened Saved tab unfetched`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.Refresh)

        assertTrue(profileDataSource.bookmarkCalls.isEmpty())
    }

    @Test
    fun `Refresh re-fetches the Saved tab once it has been opened`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        viewModel.onEvent(ProfileUiEvent.Refresh)

        assertEquals(2, profileDataSource.bookmarkCalls.size)
    }

    @Test
    fun `likes are absent until the Likes tab is shown`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertNull((viewModel.uiState.value as ProfileUiState.Loaded).data.likes)
        assertTrue(profileDataSource.likeCalls.isEmpty())
    }

    @Test
    fun `LikesTabShown loads the liked posts with their authors`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        val card = (viewModel.uiState.value as ProfileUiState.Loaded)
            .data.likes!!
            .posts
            .single()
        assertEquals("p3", card.post.id)
        assertEquals(grace, card.author)
    }

    // Likes are public, so the tab loads on someone else's profile exactly as on your own —
    // the one behavioural difference from Saved.
    @Test
    fun `LikesTabShown loads on another user's profile too`() = runTest {
        val viewModel = viewModel(userId = "u2", currentUserId = "u1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        assertEquals(listOf("u2" to null), profileDataSource.likeCalls)
    }

    @Test
    fun `LikesTabShown fetches only once across repeat visits`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.LikesTabShown)
        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        assertEquals(1, profileDataSource.likeCalls.size)
    }

    // Opening one on-demand tab must not fetch the other.
    @Test
    fun `opening the Likes tab leaves the Saved tab unfetched`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        assertTrue(profileDataSource.bookmarkCalls.isEmpty())
        assertNull((viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks)
    }

    @Test
    fun `Refresh re-fetches the Likes tab once it has been opened`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        viewModel.onEvent(ProfileUiEvent.Refresh)

        assertEquals(2, profileDataSource.likeCalls.size)
    }

    @Test
    fun `un-bookmarking from the Saved tab removes the row`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        viewModel.onEvent(ProfileUiEvent.ToggleBookmark("p2"))

        val bookmarks = (viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks!!
        assertTrue(bookmarks.posts.isEmpty())
    }

    // The Saved tab is only ever your own, but a *liked* post can also be saved — clearing one
    // flag must not disturb the list the other flag defines.
    @Test
    fun `unliking a post leaves it on the Saved tab`() = runTest {
        val alsoSaved = savedPost.copy(isLiked = true)
        val viewModel = viewModel(bookmarks = listOf(alsoSaved), likes = listOf(alsoSaved))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.SavedTabShown)

        viewModel.onEvent(ProfileUiEvent.ToggleLike("p2"))

        val bookmarks = (viewModel.uiState.value as ProfileUiState.Loaded).data.bookmarks!!
        assertEquals(listOf("p2"), bookmarks.posts.map { it.post.id })
    }

    // Liking a post from the Posts tab puts it on the Likes tab immediately — the tabs read the
    // same entity store, so neither a re-fetch nor a prior visit to the post is needed.
    @Test
    fun `liking a post from the Posts tab adds it to the Likes tab`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.LikesTabShown)

        viewModel.onEvent(ProfileUiEvent.ToggleLike("p1"))

        val likes = (viewModel.uiState.value as ProfileUiState.Loaded).data.likes!!
        assertTrue(likes.posts.any { it.post.id == "p1" })
    }

    @Test
    fun `OpenProfile pushes the saved post's author`() {
        viewModel().onEvent(ProfileUiEvent.OpenProfile("u2"))

        assertEquals(Screen.Profile("u2"), backStack.last().screen)
    }

    @Test
    fun `ToggleFollow follows the viewed user and updates the follower count`() = runTest {
        val viewModel = viewModel(userId = "u2", currentUserId = "u1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.ToggleFollow)

        val user = (viewModel.uiState.value as ProfileUiState.Loaded).data.user
        assertTrue(user.isFollowing)
        assertEquals(1, user.followerCount)
    }

    // The follow button never moves until the server answers, so a refused follow shows nothing
    // at all where the tap happened — the announcement is the only trace the failure leaves.
    @Test
    fun `a failed follow leaves the user untouched and names the action for the screen`() = runTest {
        val userDataSource = FakeUserDataSource(mapOf("u1" to ada, "u2" to grace)).apply {
            toggleFollowError = UnknownHostException("offline")
        }
        val viewModel = viewModel(userId = "u2", currentUserId = "u1", userDataSource = userDataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.ToggleFollow)

        val user = (viewModel.uiState.value as ProfileUiState.Loaded).data.user
        assertFalse(user.isFollowing)
        assertEquals(0, user.followerCount)
        assertEquals(FailedAction.FOLLOW, viewModel.failedAction.value)
    }

    @Test
    fun `a failed delete keeps the post and names the action for the screen`() = runTest {
        val postDataSource = FakePostDataSource().apply {
            deleteError = UnknownHostException("offline")
        }
        val viewModel = viewModel(postDataSource = postDataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(ProfileUiEvent.Delete("p1"))

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(listOf(post), loaded.data.posts)
        assertEquals(FailedAction.DELETE_POST, viewModel.failedAction.value)
    }

    // Announced once and then spent, the same lifetime the feed gives its refresh error: what is
    // left in the state is what a rotation would replay.
    @Test
    fun `FailedActionShown clears the announcement`() = runTest {
        val userDataSource = FakeUserDataSource(mapOf("u1" to ada, "u2" to grace)).apply {
            toggleFollowError = UnknownHostException("offline")
        }
        val viewModel = viewModel(userId = "u2", currentUserId = "u1", userDataSource = userDataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(ProfileUiEvent.ToggleFollow)

        viewModel.onEvent(ProfileUiEvent.FailedActionShown)

        assertNull(viewModel.failedAction.value)
    }
}

private class GatedUserDataSource(
    private val gate: CompletableDeferred<Unit>,
    private val user: User,
    private var failuresBeforeGate: Int = 0,
) : UserDataSource {

    override suspend fun fetch(userId: UserId): User? {
        if (failuresBeforeGate > 0) {
            failuresBeforeGate--
            throw UnknownHostException("offline")
        }
        gate.await()
        return user.takeIf { it.id == userId }
    }

    override suspend fun update(userId: UserId, update: ProfileUpdate): User =
        throw UnsupportedOperationException()

    override suspend fun toggleFollow(user: User): User = throw UnsupportedOperationException()
}
