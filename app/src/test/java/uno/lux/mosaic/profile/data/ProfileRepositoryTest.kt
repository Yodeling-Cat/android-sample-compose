package uno.lux.mosaic.profile.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import java.time.Instant

class ProfileRepositoryTest {

    private val adaPost = post(id = "p1", authorId = "u1")
    private val gracePost = post(
        id = "p2",
        authorId = "u2",
        createdAt = newer,
        isLiked = true,
        isBookmarked = true,
    )
    private val grace = User(id = "u2", nickname = "Grace", handle = "@grace")

    private fun bookmarksDataSource(
        posts: List<Post>,
        users: List<User> = emptyList(),
    ) = FakeProfileDataSource(
        bookmarks = mapOf("u1" to mapOf(null to PostsPage(posts, users, null, false))),
    )

    private fun likesDataSource(
        posts: List<Post>,
        users: List<User> = emptyList(),
        userId: UserId = "u1",
        cursor: String? = null,
        hasMore: Boolean = false,
    ) = FakeProfileDataSource(
        likes = mapOf(userId to mapOf(null to PostsPage(posts, users, cursor, hasMore))),
    )

    private fun userRepo() = UserRepository(FakeUserDataSource())

    private fun postRepo(userRepo: UserRepository = userRepo()) =
        PostRepository(FakePostDataSource(), userRepo)

    private fun repository(
        dataSource: ProfileDataSource = FakeProfileDataSource(),
        postRepo: PostRepository = postRepo(),
        userRepo: UserRepository = userRepo(),
        currentUserId: UserId = "u1",
    ) = ProfileRepository(dataSource, postRepo, userRepo, currentUserId)

    @Test
    fun `profile is empty before refresh`() = runTest {
        val profile = repository().profile("u1").first()

        assertEquals("u1", profile.userId)
        assertEquals(0, profile.postsCount)
    }

    @Test
    fun `postIds is empty before refresh`() = runTest {
        assertTrue(repository().postIds("u1").first().isEmpty())
    }

    @Test
    fun `hasMorePosts is false before refresh`() = runTest {
        assertFalse(repository().hasMorePosts("u1").first())
    }

    @Test
    fun `refresh populates profile with data source result`() = runTest {
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf("u1" to refreshData(posts = listOf(adaPost))),
        )
        val repo = repository(dataSource)

        repo.refresh("u1")

        val profile = repo.profile("u1").first()
        assertEquals("u1", profile.userId)
        assertEquals(1, profile.postsCount)
    }

    @Test
    fun `refresh populates postIds from data source result`() = runTest {
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf("u1" to refreshData(posts = listOf(adaPost, gracePost))),
        )
        val repo = repository(dataSource)

        repo.refresh("u1")

        assertEquals(listOf("p1", "p2"), repo.postIds("u1").first())
    }

    @Test
    fun `refresh ingests posts into the post repository`() = runTest {
        val postRepo = postRepo()
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf("u1" to refreshData(posts = listOf(adaPost))),
        )
        val repo = repository(dataSource, postRepo)

        repo.refresh("u1")

        assertEquals("p1", postRepo.entities.first()["p1"]?.id)
    }

    // The profile's own posts sideload their author like every other list, so a profile opened
    // cold has the user it needs to draw a row without a second request having landed.
    @Test
    fun `refresh ingests the sideloaded author into the user repository`() = runTest {
        val ada = User(id = "u1", nickname = "Ada", handle = "@ada")
        val userRepo = userRepo()
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf("u1" to refreshData(posts = listOf(adaPost), users = listOf(ada))),
        )
        val repo = repository(dataSource, userRepo = userRepo)

        repo.refresh("u1")

        assertEquals(ada, userRepo.users.first()["u1"])
    }

    @Test
    fun `refresh sets hasMorePosts from data source`() = runTest {
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf(
                "u1" to refreshData(posts = listOf(adaPost), cursor = "c2", hasMore = true),
            ),
        )
        val repo = repository(dataSource)

        repo.refresh("u1")

        assertTrue(repo.hasMorePosts("u1").first())
    }

    @Test
    fun `loadMorePosts appends post IDs`() = runTest {
        val p3 = post("p3", "u1")
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf(
                "u1" to refreshData(posts = listOf(adaPost), cursor = "c2", hasMore = true),
            ),
            morePosts = mapOf("u1" to PostsPage(listOf(p3), emptyList(), null, false)),
        )
        val repo = repository(dataSource)
        repo.refresh("u1")

        repo.loadMorePosts("u1")

        assertEquals(listOf("p1", "p3"), repo.postIds("u1").first())
    }

    @Test
    fun `loadMorePosts ingests the next page's author`() = runTest {
        val userRepo = userRepo()
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf(
                "u1" to refreshData(posts = listOf(adaPost), cursor = "c2", hasMore = true),
            ),
            morePosts = mapOf(
                "u1" to PostsPage(listOf(post("p3", "u2")), listOf(grace), null, false),
            ),
        )
        val repo = repository(dataSource, userRepo = userRepo)
        repo.refresh("u1")

        repo.loadMorePosts("u1")

        assertEquals(grace, userRepo.users.first()["u2"])
    }

    @Test
    fun `the Saved tab is null before it is asked for`() = runTest {
        val dataSource = FakeProfileDataSource()
        val repo = repository(dataSource)

        repo.refresh("u1")

        assertNull(repo.saved("u1").ids.first())
        // A profile load must not reach for a list nobody has opened.
        assertTrue(dataSource.bookmarkCalls.isEmpty())
    }

    @Test
    fun `loading the Saved tab populates its ids`() = runTest {
        val repo = repository(bookmarksDataSource(listOf(gracePost)))
        val saved = repo.saved("u1")

        saved.ensureLoaded()

        assertEquals(listOf("p2"), saved.ids.first())
    }

    // Loading once is the list's own decision rather than a guard every caller has to remember:
    // the Saved tab is re-shown on every switch back to it, and only the first costs a request.
    @Test
    fun `ensureLoaded fetches once across repeat calls`() = runTest {
        val dataSource = bookmarksDataSource(listOf(gracePost))
        val repo = repository(dataSource)
        val saved = repo.saved("u1")

        saved.ensureLoaded()
        saved.ensureLoaded()

        assertEquals(1, dataSource.bookmarkCalls.size)
    }

    @Test
    fun `refreshIfLoaded leaves an unopened tab unfetched`() = runTest {
        val dataSource = bookmarksDataSource(listOf(gracePost))
        val repo = repository(dataSource)

        repo.saved("u1").refreshIfLoaded()

        assertTrue(dataSource.bookmarkCalls.isEmpty())
        assertNull(repo.saved("u1").ids.first())
    }

    @Test
    fun `refreshIfLoaded re-fetches a tab that was opened`() = runTest {
        val dataSource = bookmarksDataSource(listOf(gracePost))
        val repo = repository(dataSource)
        val saved = repo.saved("u1")
        saved.ensureLoaded()

        saved.refreshIfLoaded()

        assertEquals(2, dataSource.bookmarkCalls.size)
    }

    // An empty list is still a loaded one — that is what tells the Saved tab to show
    // "nothing saved" rather than keep spinning.
    @Test
    fun `an empty Saved tab is loaded, not pending`() = runTest {
        val repo = repository(bookmarksDataSource(emptyList()))
        val saved = repo.saved("u1")

        saved.ensureLoaded()

        assertEquals(emptyList<String>(), saved.ids.first())
    }

    @Test
    fun `loading the Saved tab ingests its posts and their authors`() = runTest {
        val postRepo = postRepo()
        val userRepo = userRepo()
        val repo =
            repository(bookmarksDataSource(listOf(gracePost), listOf(grace)), postRepo, userRepo)

        repo.saved("u1").ensureLoaded()

        assertEquals("p2", postRepo.entities.first()["p2"]?.id)
        // Saved posts are by arbitrary authors, so they arrive with the page.
        assertEquals(grace, userRepo.users.first()["u2"])
    }

    @Test
    fun `the Saved tab's loadMore appends the next page`() = runTest {
        val p3 = post(id = "p3", authorId = "u3", createdAt = older, isBookmarked = true)
        val dataSource = FakeProfileDataSource(
            bookmarks = mapOf(
                "u1" to mapOf(
                    null to PostsPage(listOf(gracePost), listOf(grace), "c2", true),
                    "c2" to PostsPage(listOf(p3), emptyList(), null, false),
                ),
            ),
        )
        val repo = repository(dataSource)
        val saved = repo.saved("u1")
        saved.ensureLoaded()

        saved.loadMore()

        assertEquals(listOf("p2", "p3"), saved.ids.first())
        assertFalse(saved.hasMore.first())
    }

    @Test
    fun `the Saved tab's loadMore is a no-op when hasMore is false`() = runTest {
        val dataSource = bookmarksDataSource(listOf(gracePost))
        val repo = repository(dataSource)
        val saved = repo.saved("u1")
        saved.ensureLoaded()

        saved.loadMore()

        assertEquals(1, dataSource.bookmarkCalls.size)
    }

    @Test
    fun `the Saved tab's loadMore is a no-op before the first page has loaded`() = runTest {
        val dataSource = bookmarksDataSource(listOf(gracePost))
        val repo = repository(dataSource)
        val saved = repo.saved("u1")

        saved.loadMore()

        assertTrue(dataSource.bookmarkCalls.isEmpty())
        assertNull(saved.ids.first())
    }

    // The saved posts resolve through PostRepository like the profile's own, so a like
    // toggled anywhere lands in the Saved tab without this repository being involved.
    @Test
    fun `a saved post reflects a like toggled through the post repository`() = runTest {
        val savedButUnliked = post(id = "p2", authorId = "u2", isBookmarked = true)
        val postRepo = postRepo()
        val repo = repository(bookmarksDataSource(listOf(savedButUnliked), listOf(grace)), postRepo)
        val saved = repo.saved("u1")
        saved.ensureLoaded()

        postRepo.toggleLike("p2")

        assertTrue(
            postRepo.entities
                .first()
                .getValue("p2")
                .isLiked,
        )
        // The like is not what puts a post on the Saved tab, so the row stays.
        assertEquals(listOf("p2"), saved.ids.first())
    }

    // Membership moves both ways: the list is derived from the entity rather than a copy of it,
    // so un-saving drops the row and saving again is a free undo of the accidental tap.
    @Test
    fun `the Saved tab follows the bookmark flag in both directions`() = runTest {
        val postRepo = postRepo()
        val repo = repository(bookmarksDataSource(listOf(gracePost), listOf(grace)), postRepo)
        val saved = repo.saved("u1")
        saved.ensureLoaded()

        postRepo.toggleBookmark("p2")
        assertEquals(emptyList<String>(), saved.ids.first())

        postRepo.toggleBookmark("p2")
        assertEquals(listOf("p2"), saved.ids.first())
    }

    @Test
    fun `the Likes tab is null before it is asked for`() = runTest {
        val dataSource = FakeProfileDataSource()
        val repo = repository(dataSource)

        repo.refresh("u1")

        assertNull(repo.liked("u1").ids.first())
        assertTrue(dataSource.likeCalls.isEmpty())
    }

    @Test
    fun `loading the Likes tab populates its ids and ingests the authors`() = runTest {
        val userRepo = userRepo()
        val repo =
            repository(likesDataSource(listOf(gracePost), listOf(grace)), userRepo = userRepo)
        val liked = repo.liked("u1")

        liked.ensureLoaded()

        assertEquals(listOf("p2"), liked.ids.first())
        assertEquals(grace, userRepo.users.first()["u2"])
    }

    // The two tabs are separate lists behind separate endpoints; filling one must leave
    // the other untouched and still unloaded.
    @Test
    fun `likes and bookmarks are independent lists`() = runTest {
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u1" to
                    mapOf(null to PostsPage(listOf(gracePost), emptyList(), null, false)),
            ),
            bookmarks = mapOf(
                "u1" to
                    mapOf(null to PostsPage(listOf(adaPost), emptyList(), null, false)),
            ),
        )
        val repo = repository(dataSource)

        repo.liked("u1").ensureLoaded()

        assertEquals(listOf("p2"), repo.liked("u1").ids.first())
        assertNull(repo.saved("u1").ids.first())
        assertTrue(dataSource.bookmarkCalls.isEmpty())
    }

    @Test
    fun `the Likes tab's loadMore appends the next page`() = runTest {
        val p3 = post(id = "p3", authorId = "u3", createdAt = older, isLiked = true)
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u1" to mapOf(
                    null to PostsPage(listOf(gracePost), listOf(grace), "c2", true),
                    "c2" to PostsPage(listOf(p3), emptyList(), null, false),
                ),
            ),
        )
        val repo = repository(dataSource)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        liked.loadMore()

        assertEquals(listOf("p2", "p3"), liked.ids.first())
        assertFalse(liked.hasMore.first())
    }

    @Test
    fun `the Likes tab's loadMore is a no-op when hasMore is false`() = runTest {
        val dataSource = likesDataSource(listOf(gracePost))
        val repo = repository(dataSource)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        liked.loadMore()

        assertEquals(1, dataSource.likeCalls.size)
    }

    @Test
    fun `unliking drops the post from the Likes tab`() = runTest {
        val postRepo = postRepo()
        val repo = repository(likesDataSource(listOf(gracePost), listOf(grace)), postRepo)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        postRepo.toggleLike("p2")

        assertEquals(emptyList<String>(), liked.ids.first())
    }

    // `isLiked` describes the *viewer*, so on someone else's Likes tab it says nothing about why
    // a post is on the list — narrowing there would hide posts the owner still likes.
    @Test
    fun `another user's Likes tab is not narrowed by the viewer's own like state`() = runTest {
        val unlikedByViewer = post(id = "p4", authorId = "u3", isLiked = false)
        val repo = repository(
            likesDataSource(listOf(unlikedByViewer), userId = "u2"),
            currentUserId = "u1",
        )
        val liked = repo.liked("u2")

        liked.ensureLoaded()

        assertEquals(listOf("p4"), liked.ids.first())
    }

    // The point of deriving the list: a post liked anywhere else — the Posts tab, the feed — joins
    // the Likes tab straight away, not only if it happened to be in a page already fetched.
    @Test
    fun `liking a post not in the fetched page adds it to the Likes tab`() = runTest {
        val postRepo = postRepo()
        postRepo.ingest(listOf(adaPost))
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u1" to mapOf(null to PostsPage(emptyList(), emptyList(), null, false)),
            ),
        )
        val repo = repository(dataSource, postRepo)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        postRepo.toggleLike("p1")

        assertEquals(listOf("p1"), liked.ids.first())
    }

    // It lands in the server's own order rather than at whichever end is convenient, so the tab
    // reads the same before and after the next refresh.
    @Test
    fun `a newly liked post is inserted in keyset order`() = runTest {
        val postRepo = postRepo()
        val middle = post(id = "p9", authorId = "u3", createdAt = older.plusSeconds(50))
        postRepo.ingest(listOf(middle))
        val newest = post(id = "p3", authorId = "u3", createdAt = older, isLiked = true)
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u1" to
                    mapOf(
                        null to
                            PostsPage(listOf(gracePost, newest), emptyList(), null, false),
                    ),
            ),
        )
        val repo = repository(dataSource, postRepo)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        postRepo.toggleLike("p9")

        assertEquals(listOf("p2", "p9", "p3"), liked.ids.first())
    }

    // A post older than everything loaded belongs to a page the server has not sent. Slotting it
    // in at the end would put it ahead of posts that outrank it, so it waits for its page.
    @Test
    fun `a liked post below the loaded window waits for its page`() = runTest {
        val postRepo = postRepo()
        val ancient = post(id = "p9", authorId = "u3", createdAt = Instant.EPOCH)
        postRepo.ingest(listOf(ancient))
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u1" to
                    mapOf(null to PostsPage(listOf(gracePost), emptyList(), "c2", true)),
            ),
        )
        val repo = repository(dataSource, postRepo)
        val liked = repo.liked("u1")
        liked.ensureLoaded()

        postRepo.toggleLike("p9")

        assertEquals(listOf("p2"), liked.ids.first())
    }

    // Someone else's Likes tab is echoed as fetched: the viewer's own flags describe the viewer,
    // so liking a post of your own must not add it to their list.
    @Test
    fun `liking a post does not add it to another user's Likes tab`() = runTest {
        val postRepo = postRepo()
        postRepo.ingest(listOf(adaPost))
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u2" to
                    mapOf(null to PostsPage(listOf(gracePost), emptyList(), null, false)),
            ),
        )
        val repo = repository(dataSource, postRepo, currentUserId = "u1")
        val liked = repo.liked("u2")
        liked.ensureLoaded()

        postRepo.toggleLike("p1")

        assertEquals(listOf("p2"), liked.ids.first())
    }

    // The tab is handed out with its user bound, so two users' lists cannot be crossed even
    // though they share one store.
    @Test
    fun `a tab bound to one user is unaffected by another user's load`() = runTest {
        val dataSource = FakeProfileDataSource(
            likes = mapOf(
                "u2" to mapOf(null to PostsPage(listOf(gracePost), emptyList(), null, false)),
            ),
        )
        val repo = repository(dataSource, currentUserId = "u3")

        repo.liked("u2").ensureLoaded()

        assertNull(repo.liked("u1").ids.first())
        assertEquals(listOf("u2" to null), dataSource.likeCalls)
    }

    @Test
    fun `loadMorePosts is a no-op when hasMore is false`() = runTest {
        val dataSource = FakeProfileDataSource(
            refreshData = mapOf("u1" to refreshData(posts = listOf(adaPost))),
        )
        val repo = repository(dataSource)
        repo.refresh("u1")

        repo.loadMorePosts("u1")

        assertEquals(listOf("p1"), repo.postIds("u1").first())
    }
}

private fun refreshData(
    posts: List<Post> = emptyList(),
    users: List<User> = emptyList(),
    cursor: String? = null,
    hasMore: Boolean = false,
) = ProfileRefreshData(
    postsCount = posts.size,
    page = PostsPage(posts, users, cursor, hasMore),
)

// These lists come back in (createdAt, id) descending order, so a later page is always older
// than the one before it. Fixtures spanning pages need distinct timestamps to be realistic.
private val newer: Instant = Instant.EPOCH.plusSeconds(200)
private val older: Instant = Instant.EPOCH.plusSeconds(100)

private fun post(
    id: String,
    authorId: UserId,
    createdAt: Instant = Instant.EPOCH,
    isLiked: Boolean = false,
    isBookmarked: Boolean = false,
) = Post(
    id = id,
    url = testPostUrl(id),
    authorId = authorId,
    title = "Title $id",
    body = "Body $id",
    createdAt = createdAt,
    likeCount = 0,
    commentCount = 0,
    isLiked = isLiked,
    isBookmarked = isBookmarked,
)
