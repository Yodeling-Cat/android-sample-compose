package uno.lux.mosaic.feed.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

class FeedRepositoryTest {

    private fun userRepo() = UserRepository(FakeUserDataSource())

    private fun postRepo(userRepo: UserRepository = userRepo()) =
        PostRepository(FakePostDataSource(), userRepo)

    private fun feedRepo(
        dataSource: FeedDataSource = FakeFeedDataSource(),
        postRepo: PostRepository = postRepo(),
        userRepo: UserRepository = userRepo(),
    ) = FeedRepository(dataSource, postRepo, userRepo)

    @Test
    fun `feedState is NotLoaded before any refresh`() = runTest {
        assertEquals(FeedState.NotLoaded, feedRepo().feedState.first())
    }

    @Test
    fun `reset returns feedState to NotLoaded after a successful refresh`() = runTest {
        val repo = feedRepo(
            FakeFeedDataSource(
                listOf(
                    FeedPage(
                        listOf(post("p1")),
                        emptyList(),
                        null,
                        false,
                    ),
                ),
            ),
        )
        repo.refresh()

        repo.reset()

        assertEquals(FeedState.NotLoaded, repo.feedState.first())
    }

    @Test
    fun `refresh transitions feedState to Loaded with feed-ordered post IDs`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1"), post("p2")), emptyList(), null, false)),
        )
        val repo = feedRepo(dataSource)

        repo.refresh()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf("p1", "p2"), loaded.postIds)
    }

    @Test
    fun `refresh ingests post entities into the post repository`() = runTest {
        val postRepo = postRepo()
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1")), emptyList(), null, false)),
        )
        val repo = feedRepo(dataSource, postRepo)

        repo.refresh()

        assertEquals("p1", postRepo.entities.first()["p1"]?.id)
    }

    @Test
    fun `refresh ingests users into the user repository`() = runTest {
        val userRepo = userRepo()
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(emptyList(), listOf(user("u1", "Ada")), null, false)),
        )
        val repo = feedRepo(dataSource, userRepo = userRepo)

        repo.refresh()

        assertEquals("Ada", userRepo.user("u1").first()?.nickname)
    }

    @Test
    fun `refresh without users leaves the user repository unchanged`() = runTest {
        val userRepo = userRepo()
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1")), emptyList(), null, false)),
        )
        val repo = feedRepo(dataSource, userRepo = userRepo)

        repo.refresh()

        assertTrue(userRepo.users.first().isEmpty())
    }

    @Test
    fun `hasMore reflects the page flag after refresh`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1")), emptyList(), "c2", true)),
        )
        val repo = feedRepo(dataSource)

        repo.refresh()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `loadMore appends the next page of post IDs`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(
                FeedPage(listOf(post("p1")), emptyList(), "c2", true),
                FeedPage(listOf(post("p2")), emptyList(), null, false),
            ),
        )
        val repo = feedRepo(dataSource)
        repo.refresh()

        repo.loadMore()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf("p1", "p2"), loaded.postIds)
    }

    @Test
    fun `loadMore clears hasMore when the last page is fetched`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(
                FeedPage(listOf(post("p1")), emptyList(), "c2", true),
                FeedPage(listOf(post("p2")), emptyList(), null, false),
            ),
        )
        val repo = feedRepo(dataSource)
        repo.refresh()

        repo.loadMore()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertFalse(loaded.hasMore)
    }

    @Test
    fun `loadMore is a no-op when hasMore is false`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1")), emptyList(), null, false)),
        )
        val repo = feedRepo(dataSource)
        repo.refresh()

        repo.loadMore()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf("p1"), loaded.postIds)
    }

    @Test
    fun `publish puts the new post at the head of a loaded feed`() = runTest {
        val dataSource = FakeFeedDataSource(
            pages = listOf(FeedPage(listOf(post("p1")), emptyList(), null, false)),
        )
        val repo = feedRepo(dataSource)
        repo.refresh()

        val postId = repo.publish(NewPost(title = "Title", body = "Body"))

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf(postId, "p1"), loaded.postIds)
    }

    @Test
    fun `publish stores the new post and its author in the entity repositories`() = runTest {
        val userRepo = userRepo()
        val postRepo = postRepo(userRepo)
        val repo = feedRepo(postRepo = postRepo, userRepo = userRepo)

        val postId = repo.publish(NewPost(title = "Title", body = "Body"))

        assertEquals("Title", postRepo.entities.first()[postId]?.title)
        assertEquals("Ada", userRepo.user("u1").first()?.nickname)
    }

    @Test
    fun `publish leaves a feed that has not loaded yet NotLoaded`() = runTest {
        val repo = feedRepo()

        repo.publish(NewPost(title = "Title", body = "Body"))

        assertEquals(FeedState.NotLoaded, repo.feedState.first())
    }

    @Test
    fun `refresh resets the post list and cursor`() = runTest {
        val page1 = FeedPage(listOf(post("p1")), emptyList(), "c2", true)
        val page2 = FeedPage(listOf(post("p2")), emptyList(), null, false)
        val dataSource = FakeFeedDataSource(pages = listOf(page1, page2, page1))
        val repo = feedRepo(dataSource)
        repo.refresh()
        repo.loadMore()

        repo.refresh()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf("p1"), loaded.postIds)
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `a page that lands after a refresh restarted the feed is dropped`() = runTest {
        val dataSource = GatedFeedDataSource()
        val repo = feedRepo(dataSource)
        launch { repo.refresh() }
        runCurrent()
        dataSource.calls[0].answer.complete(FeedPage(listOf(post("p1")), emptyList(), "c2", true))
        runCurrent()

        launch { repo.loadMore() }
        runCurrent()
        launch { repo.refresh() }
        runCurrent()
        dataSource.calls[2].answer.complete(FeedPage(listOf(post("p0"), post("p1")), emptyList(), "c3", true))
        runCurrent()
        dataSource.calls[1].answer.complete(FeedPage(listOf(post("p2")), emptyList(), null, false))
        runCurrent()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf("p0", "p1"), loaded.postIds)
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `loadMore after a refresh landed mid-page continues from the refreshed cursor`() = runTest {
        val dataSource = GatedFeedDataSource()
        val repo = feedRepo(dataSource)
        launch { repo.refresh() }
        runCurrent()
        dataSource.calls[0].answer.complete(FeedPage(listOf(post("p1")), emptyList(), "c2", true))
        runCurrent()
        launch { repo.loadMore() }
        runCurrent()
        launch { repo.refresh() }
        runCurrent()
        dataSource.calls[2].answer.complete(FeedPage(listOf(post("p0")), emptyList(), "c3", true))
        runCurrent()
        dataSource.calls[1].answer.complete(FeedPage(listOf(post("p2")), emptyList(), null, false))
        runCurrent()

        backgroundScope.launch { repo.loadMore() }
        runCurrent()

        assertEquals("c3", dataSource.calls[3].cursor)
    }

    @Test
    fun `a post published while loadMore is on the wire stays at the head of the feed`() = runTest {
        val dataSource = GatedFeedDataSource()
        val repo = feedRepo(dataSource)
        launch { repo.refresh() }
        runCurrent()
        dataSource.calls[0].answer.complete(FeedPage(listOf(post("p1")), emptyList(), "c2", true))
        runCurrent()
        launch { repo.loadMore() }
        runCurrent()

        val postId = repo.publish(NewPost(title = "Title", body = "Body"))
        dataSource.calls[1].answer.complete(FeedPage(listOf(post("p2")), emptyList(), null, false))
        runCurrent()

        val loaded = repo.feedState.first() as FeedState.Loaded
        assertEquals(listOf(postId, "p1", "p2"), loaded.postIds)
    }
}

private fun post(id: String) = Post(
    id = id,
    url = testPostUrl(id),
    authorId = "u1",
    title = "Title $id",
    body = "Body $id",
    createdAt = Instant.EPOCH,
    likeCount = 0,
    commentCount = 0,
)

private fun user(id: String, nickname: String) = User(id = id, nickname = nickname, handle = "@$id")
