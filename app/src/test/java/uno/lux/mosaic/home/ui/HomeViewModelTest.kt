package uno.lux.mosaic.home.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.feed.data.FakeFeedDataSource
import uno.lux.mosaic.feed.data.FeedDataSource
import uno.lux.mosaic.feed.data.FeedPage
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.ui.PostCardData
import uno.lux.mosaic.post.ui.ReportSendState
import uno.lux.mosaic.settings.data.InMemorySettingsRepository
import uno.lux.mosaic.settings.data.SettingsRepository
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video
import java.net.UnknownHostException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : ViewModelTest() {

    private val author = User(id = "u1", nickname = "Ada", handle = "@ada")
    private val author2 = User(id = "u2", nickname = "Bob", handle = "@bob")
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
    private val post2 = Post(
        id = "p2",
        url = testPostUrl("p2"),
        authorId = "u2",
        title = "Title 2",
        body = "Body 2",
        createdAt = Instant.EPOCH,
        likeCount = 5,
        commentCount = 0,
    )

    // The real Navigator drives a plain list, so navigation tests assert on the stack directly.
    private val backStack = backStackOf(Screen.Shell)
    private val navigator = Navigator().apply { attach(backStack) }

    private fun viewModel(
        feedDataSource: FeedDataSource =
            FakeFeedDataSource(listOf(FeedPage(listOf(post), listOf(author), null, false))),
        currentUserId: UserId = "u1",
        settingsRepository: SettingsRepository = InMemorySettingsRepository(),
        postDataSource: FakePostDataSource = FakePostDataSource(),
    ): HomeViewModel {
        val userRepo = UserRepository(FakeUserDataSource())
        val postRepo = PostRepository(postDataSource, userRepo)
        val feedRepo = FeedRepository(feedDataSource, postRepo, userRepo)
        return HomeViewModel(feedRepo, postRepo, userRepo, settingsRepository, navigator, currentUserId)
    }

    @Test
    fun `uiState is Loading until something collects it`() {
        assertEquals(HomeUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `uiState exposes the feed once collected`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertEquals(listOf(PostCardData(post, author, isOwn = true)), feed.posts)
        assertTrue(feed.endReached)
    }

    @Test
    fun `ToggleLike likes the post through the repository`() = runTest {
        // The count in the answer is the server's, so the fake is told what it started from.
        val viewModel = viewModel(
            postDataSource = FakePostDataSource().apply { likeCounts["p1"] = post.likeCount },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(HomeUiEvent.ToggleLike("p1"))

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertTrue(
            feed.posts
                .single()
                .post.isLiked,
        )
        assertEquals(
            11,
            feed.posts
                .single()
                .post.likeCount,
        )
    }

    @Test
    fun `ToggleBookmark bookmarks the post through the repository`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(HomeUiEvent.ToggleBookmark("p1"))

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertTrue(
            feed.posts
                .single()
                .post.isBookmarked,
        )
    }

    @Test
    fun `a card is marked own only when the signed-in user wrote it`() = runTest {
        val viewModel = viewModel(currentUserId = "u2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertFalse(feed.posts.single().isOwn)
    }

    @Test
    fun `Delete drops the post from the feed`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(HomeUiEvent.Delete("p1"))

        assertTrue((viewModel.uiState.value as HomeUiState.Feed).posts.isEmpty())
    }

    // The confirmation dialog is gone by the time the server refuses, so the failure has to be
    // named somewhere the screen can announce it — and the post must stay exactly where it was.
    @Test
    fun `a failed delete keeps the post and names the action for the screen`() = runTest {
        val dataSource = FakePostDataSource().apply { deleteError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(HomeUiEvent.Delete("p1"))

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertEquals(listOf(PostCardData(post, author, isOwn = true)), feed.posts)
        assertEquals(FailedAction.DELETE_POST, viewModel.failedAction.value)
    }

    @Test
    fun `a delete that goes through announces nothing`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onEvent(HomeUiEvent.Delete("p1"))

        assertNull(viewModel.failedAction.value)
    }

    // Announced once and then spent, the same lifetime the refresh error has: what is left in
    // the state is what a rotation would replay.
    @Test
    fun `FailedActionShown clears the announcement`() = runTest {
        val dataSource = FakePostDataSource().apply { deleteError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onEvent(HomeUiEvent.Delete("p1"))

        viewModel.onEvent(HomeUiEvent.FailedActionShown)

        assertNull(viewModel.failedAction.value)
    }

    // Reporting from the feed reports the card that was tapped and leaves the feed alone —
    // the post is still there, and still says what it said.
    @Test
    fun `Report reports the post without changing the feed`() = runTest {
        val dataSource = FakePostDataSource()
        val viewModel = viewModel(postDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val before = (viewModel.uiState.value as HomeUiState.Feed).posts

        viewModel.onEvent(HomeUiEvent.Report("p1", ReportReason.VIOLENCE, ""))

        assertEquals(
            listOf(FakePostDataSource.Report("p1", ReportReason.VIOLENCE, "")),
            dataSource.reports,
        )
        assertEquals(before, (viewModel.uiState.value as HomeUiState.Feed).posts)
    }

    // The feed's report goes through the same send state the detail page's does, so the card's
    // dialog can stay up for it — and a failure stays out of the screen's snackbar.
    @Test
    fun `a failed report is a state for the card's dialog, not an announcement`() = runTest {
        val dataSource = FakePostDataSource().apply { reportError = UnknownHostException("offline") }
        val viewModel = viewModel(postDataSource = dataSource)

        viewModel.onEvent(HomeUiEvent.Report("p1", ReportReason.VIOLENCE, ""))

        assertEquals(ReportSendState.FAILED, viewModel.reportSend.value)
        assertNull(viewModel.failedAction.value)
    }

    @Test
    fun `a report the server takes reaches SENT, and closing the dialog spends it`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(HomeUiEvent.Report("p1", ReportReason.VIOLENCE, ""))
        assertEquals(ReportSendState.SENT, viewModel.reportSend.value)

        viewModel.onEvent(HomeUiEvent.CloseReport)

        assertEquals(ReportSendState.IDLE, viewModel.reportSend.value)
    }

    @Test
    fun `uiState stays Loading while the initial load is active`() = runTest {
        val dataSource = SuspendingFeedDataSource()
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)

        dataSource.complete()

        assertTrue(viewModel.uiState.value is HomeUiState.Feed)
    }

    @Test
    fun `uiState shows Error when the initial load fails`() = runTest {
        val viewModel = viewModel(feedDataSource = FailingFeedDataSource())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue(viewModel.uiState.value is HomeUiState.Error)
    }

    // A refresh in a tunnel must not take away the feed the user was reading: the posts stay on
    // screen and the failure rides along for the screen to report transiently.
    @Test
    fun `a failed refresh keeps the loaded feed and carries the error alongside it`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), null, false))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        assertTrue(viewModel.uiState.value is HomeUiState.Feed)

        dataSource.failNext = true
        viewModel.onEvent(HomeUiEvent.Refresh)

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertEquals(listOf(PostCardData(post, author, isOwn = true)), feed.posts)
        assertEquals(AppError.NoConnection, feed.refreshError)
    }

    // Announced once and then spent: what is left in the state is what a rotation would replay,
    // and a failure the user has already read is not worth reading twice.
    @Test
    fun `RefreshErrorShown clears the transient error without touching the feed`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), null, false))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        dataSource.failNext = true
        viewModel.onEvent(HomeUiEvent.Refresh)

        viewModel.onEvent(HomeUiEvent.RefreshErrorShown)

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertNull(feed.refreshError)
        assertEquals(listOf(PostCardData(post, author, isOwn = true)), feed.posts)
    }

    @Test
    fun `a refresh that succeeds clears the previous failure`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), null, false))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        dataSource.failNext = true
        viewModel.onEvent(HomeUiEvent.Refresh)

        dataSource.failNext = false
        viewModel.onEvent(HomeUiEvent.Refresh)

        assertNull((viewModel.uiState.value as HomeUiState.Feed).refreshError)
    }

    @Test
    fun `a failed load-more is carried on the feed for the footer to offer a retry`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), "c2", true))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        dataSource.failNext = true

        viewModel.onEvent(HomeUiEvent.LoadMore)

        assertTrue((viewModel.uiState.value as HomeUiState.Feed).loadMoreFailed)
    }

    @Test
    fun `loading more again clears the failure`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), "c2", true))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        dataSource.failNext = true
        viewModel.onEvent(HomeUiEvent.LoadMore)
        dataSource.failNext = false

        viewModel.onEvent(HomeUiEvent.LoadMore)

        assertFalse((viewModel.uiState.value as HomeUiState.Feed).loadMoreFailed)
    }

    @Test
    fun `a refresh clears a load-more failure, since it restarts the feed`() = runTest {
        val dataSource = FlakyFeedDataSource(FeedPage(listOf(post), listOf(author), "c2", true))
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        dataSource.failNext = true
        viewModel.onEvent(HomeUiEvent.LoadMore)
        dataSource.failNext = false

        viewModel.onEvent(HomeUiEvent.Refresh)

        assertFalse((viewModel.uiState.value as HomeUiState.Feed).loadMoreFailed)
    }

    @Test
    fun `Retry shows Loading while reloading after the feed was already loaded`() = runTest {
        val dataSource = SucceedOnceThenSuspendFeedDataSource(
            firstPage = FeedPage(listOf(post), listOf(author), null, false),
        )
        val viewModel = viewModel(feedDataSource = dataSource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        assertTrue(viewModel.uiState.value is HomeUiState.Feed)

        viewModel.onEvent(HomeUiEvent.Retry)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `Refresh raises isRefreshing until the feed data source finishes`() = runTest {
        // The initial load has to have finished, or the refresh is the duplicate fetch that
        // `Refresh is ignored while the initial load is still running` pins.
        val dataSource = SucceedOnceThenSuspendFeedDataSource(
            firstPage = FeedPage(listOf(post), listOf(author), null, false),
        )
        val viewModel = viewModel(feedDataSource = dataSource)

        assertFalse(viewModel.isRefreshing.value)
        viewModel.onEvent(HomeUiEvent.Refresh)
        assertTrue(viewModel.isRefreshing.value)
        dataSource.complete()
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `Refresh is ignored while the initial load is still running`() = runTest {
        val dataSource = SuspendingFeedDataSource()
        val viewModel = viewModel(feedDataSource = dataSource)

        viewModel.onEvent(HomeUiEvent.Refresh)

        // The load already in flight owns the spinner; a second fetch of the same page is not
        // what a pull mid-load should buy.
        assertFalse(viewModel.isRefreshing.value)
        assertEquals(1, dataSource.fetches)
    }

    @Test
    fun `post and author instances are preserved for unchanged posts on a like toggle`() = runTest {
        val viewModel = viewModel(
            feedDataSource = FakeFeedDataSource(
                listOf(FeedPage(listOf(post, post2), listOf(author, author2), null, false)),
            ),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val cardBefore = (viewModel.uiState.value as HomeUiState.Feed).posts[1]

        viewModel.onEvent(HomeUiEvent.ToggleLike(post.id))

        // PostCard takes the post and the author as separate parameters, so these two instances
        // are what strong skipping compares. The card wrapping them is rebuilt every emission.
        val cardAfter = (viewModel.uiState.value as HomeUiState.Feed).posts[1]
        assertSame(cardBefore.post, cardAfter.post)
        assertSame(cardBefore.author, cardAfter.author)
    }

    @Test
    fun `OpenSettings pushes the settings page`() {
        viewModel().onEvent(HomeUiEvent.OpenSettings)

        assertEquals(Screen.Settings, backStack.last().screen)
    }

    @Test
    fun `OpenSettings does not stack a second settings page`() {
        val viewModel = viewModel()

        viewModel.onEvent(HomeUiEvent.OpenSettings)
        viewModel.onEvent(HomeUiEvent.OpenSettings)

        assertEquals(listOf(Screen.Shell, Screen.Settings), backStack.screens())
    }

    @Test
    fun `OpenProfile pushes the author's profile page`() {
        viewModel().onEvent(HomeUiEvent.OpenProfile("u1"))

        assertEquals(Screen.Profile("u1"), backStack.last().screen)
    }

    @Test
    fun `OpenPost pushes the post's detail page`() {
        viewModel().onEvent(HomeUiEvent.OpenPost("p1"))

        assertEquals(Screen.PostDetail("p1"), backStack.last().screen)
    }

    @Test
    fun `OpenVideo pushes the fullscreen player for that video`() {
        val video = Video(
            id = "v1",
            title = "Talk",
            durationSeconds = 95,
            videoUrl = "https://example.test/v1.mp4",
        )

        viewModel().onEvent(HomeUiEvent.OpenVideo(video))

        assertEquals(
            Screen.FullscreenVideo(url = "https://example.test/v1.mp4", title = "Talk"),
            backStack.last().screen,
        )
    }

    @Test
    fun `OpenAlbum pushes the album viewer at the tapped image`() {
        val images = listOf("https://example.test/1.jpg", "https://example.test/2.jpg")

        viewModel().onEvent(HomeUiEvent.OpenAlbum(images, initialIndex = 1))

        assertEquals(Screen.AlbumViewer(images, initialIndex = 1), backStack.last().screen)
    }

    @Test
    fun `endReached is false when the feed reports hasMore`() = runTest {
        val viewModel = viewModel(
            feedDataSource = FakeFeedDataSource(listOf(FeedPage(emptyList(), emptyList(), "cursor", true))),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val feed = viewModel.uiState.value as HomeUiState.Feed
        assertFalse(feed.endReached)
    }

    // The stored preference arrives asynchronously, so the StateFlow starts from the shared default
    // rather than from the repository. The repository is seeded with the *opposite* value, so a
    // ViewModel that read through to it instead of starting from the default would fail here.
    @Test
    fun `autoPlayVideos is off before anything is collected`() {
        val viewModel = viewModel(
            settingsRepository = InMemorySettingsRepository(initialAutoPlayVideos = true),
        )

        assertFalse(viewModel.autoPlayVideos.value)
    }

    @Test
    fun `autoPlayVideos follows the user's setting, including a change made while the feed is open`() = runTest {
        val settings = InMemorySettingsRepository(initialAutoPlayVideos = false)
        val viewModel = viewModel(settingsRepository = settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.autoPlayVideos.collect {}
        }

        assertFalse(viewModel.autoPlayVideos.value)

        settings.setAutoPlayVideos(true)

        assertTrue(viewModel.autoPlayVideos.value)
    }
}

private class SucceedOnceThenSuspendFeedDataSource(
    private val firstPage: FeedPage,
) : FeedDataSource {
    private val gate = CompletableDeferred<Unit>()
    private var firstFetch = true

    override suspend fun fetch(cursor: String?): FeedPage {
        if (firstFetch) {
            firstFetch = false
            return firstPage
        }
        gate.await()
        return FeedPage(emptyList(), emptyList(), null, false)
    }

    fun complete() = gate.complete(Unit)
}

private class FlakyFeedDataSource(
    private val page: FeedPage,
) : FeedDataSource {
    var failNext = false

    override suspend fun fetch(cursor: String?): FeedPage {
        if (failNext) throw UnknownHostException("offline")

        return page
    }
}

private class FailingFeedDataSource : FeedDataSource {
    override suspend fun fetch(cursor: String?): FeedPage = throw RuntimeException("network error")
}

private class SuspendingFeedDataSource : FeedDataSource {
    private val gate = CompletableDeferred<Unit>()
    var fetches = 0
        private set

    override suspend fun fetch(cursor: String?): FeedPage {
        fetches++
        gate.await()
        return FeedPage(emptyList(), emptyList(), null, false)
    }

    fun complete() = gate.complete(Unit)
}
