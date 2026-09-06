package uno.lux.mosaic.post.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

class PostRepositoryTest {

    private val unliked = post(id = "unliked", likeCount = 4, isLiked = false)
    private val liked = post(id = "liked", likeCount = 10, isLiked = true)
    private val bookmarked = post(id = "bookmarked", isBookmarked = true)
    private val author = User(id = "u-unliked", nickname = "Ada", handle = "@ada")

    private val userRepository = UserRepository(FakeUserDataSource())

    private fun repository(dataSource: PostDataSource = FakePostDataSource()) =
        PostRepository(dataSource, userRepository)

    @Test
    fun `entities starts empty`() = runTest {
        assertTrue(repository().entities.first().isEmpty())
    }

    @Test
    fun `ingest adds posts to the entity map`() = runTest {
        val repo = repository()

        repo.ingest(listOf(unliked))

        assertEquals(unliked, repo.entities.first()["unliked"])
    }

    // load() is what a screen with nothing but a post ID falls back on — a detail page restored
    // after process death, whose stores start empty.
    @Test
    fun `load stores the fetched post and seeds its sideloaded author`() = runTest {
        val dataSource = FakePostDataSource()
        dataSource.fetchable["unliked"] = PostWithUsers(unliked, listOf(author))
        val repo = repository(dataSource)

        assertEquals(unliked, repo.load("unliked"))

        assertEquals(unliked, repo.entities.first()["unliked"])
        // The author rides along, so a screen resolving the post finds its header user too.
        assertEquals(author, userRepository.users.first()[author.id])
    }

    @Test
    fun `load returns null for a post the source no longer has`() = runTest {
        val repo = repository()

        assertNull(repo.load("gone"))
        assertTrue(repo.entities.first().isEmpty())
    }

    @Test
    fun `load leaves previously ingested posts in place`() = runTest {
        val dataSource = FakePostDataSource()
        dataSource.fetchable["unliked"] = PostWithUsers(unliked, listOf(author))
        val repo = repository(dataSource)
        repo.ingest(listOf(liked))

        repo.load("unliked")

        assertEquals(liked, repo.entities.first()["liked"])
    }

    @Test
    fun `create stores the published post and seeds its sideloaded author`() = runTest {
        val repo = repository()

        val created = repo.create(NewPost(title = "Title", body = "Body"))

        assertEquals(created, repo.entities.first()[created.id])
        assertEquals("u1", userRepository.users.first()["u1"]?.id)
    }

    @Test
    fun `create leaves previously ingested posts in place`() = runTest {
        val repo = repository()
        repo.ingest(listOf(liked))

        repo.create(NewPost(title = "Title", body = "Body"))

        assertEquals(liked, repo.entities.first()["liked"])
    }

    @Test
    fun `ingest merges without evicting existing entries`() = runTest {
        val repo = repository()
        repo.ingest(listOf(liked))

        repo.ingest(listOf(unliked))

        val entities = repo.entities.first()
        assertEquals(liked, entities["liked"])
        assertEquals(unliked, entities["unliked"])
    }

    @Test
    fun `toggleLike applies the data source result to the entity map`() = runTest {
        val dataSource = FakePostDataSource().apply { likeCounts["unliked"] = 4 }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        repo.toggleLike("unliked")

        val result = repo.entities.first()["unliked"]!!
        assertTrue(result.isLiked)
        assertEquals(5, result.likeCount)
    }

    @Test
    fun `toggleLike asks for the state opposite the one the post is in`() = runTest {
        val dataSource = FakePostDataSource()
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked, liked))

        repo.toggleLike("unliked")
        repo.toggleLike("liked")

        assertEquals(listOf("unliked" to true, "liked" to false), dataSource.likeRequests)
    }

    @Test
    fun `toggleBookmark applies the data source result to the entity map`() = runTest {
        val repo = repository()
        repo.ingest(listOf(bookmarked))

        repo.toggleBookmark("bookmarked")

        assertFalse(repo.entities.first()["bookmarked"]!!.isBookmarked)
    }

    //
    // The heart is the most-tapped control in the app, so it fills on the tap rather than on the
    // round trip. [FakePostDataSource.whileInFlight] runs inside the request, which is the only
    // moment the optimistic value is the one in the store.

    @Test
    fun `toggleLike fills the heart before the request answers`() = runTest {
        var inFlight: Post? = null
        val dataSource = FakePostDataSource().apply { likeCounts["unliked"] = 4 }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        dataSource.whileInFlight = { inFlight = repo.entities.value["unliked"] }

        repo.toggleLike("unliked")

        val seen = inFlight!!
        assertTrue(seen.isLiked)
        assertEquals(5, seen.likeCount)
    }

    @Test
    fun `toggleBookmark fills the bookmark before the request answers`() = runTest {
        var inFlight: Post? = null
        val dataSource = FakePostDataSource()
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        dataSource.whileInFlight = { inFlight = repo.entities.value["unliked"] }

        repo.toggleBookmark("unliked")

        assertTrue(inFlight!!.isBookmarked)
    }

    // A tap the server refused has to come back off, or the app is showing a like nobody holds.
    @Test
    fun `a failed toggleLike puts the like fields back`() = runTest {
        val dataSource = FakePostDataSource().apply {
            likeCounts["unliked"] = 4
            setLikeError = IllegalStateException("boom")
        }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        runCatching { repo.toggleLike("unliked") }

        val result = repo.entities.first()["unliked"]!!
        assertFalse(result.isLiked)
        assertEquals(4, result.likeCount)
    }

    @Test
    fun `a failed toggleBookmark puts the bookmark back`() = runTest {
        val dataSource = FakePostDataSource().apply { setBookmarkError = IllegalStateException("boom") }
        val repo = repository(dataSource)
        repo.ingest(listOf(bookmarked))

        runCatching { repo.toggleBookmark("bookmarked") }

        assertTrue(repo.entities.first()["bookmarked"]!!.isBookmarked)
    }

    // The caller has to be able to tell a like that landed from one that did not — a failure that
    // rolled the entity back and said nothing would look identical to a tap that never happened.
    @Test
    fun `a failed toggle rethrows`() = runTest {
        val dataSource = FakePostDataSource().apply { setLikeError = IllegalStateException("boom") }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.toggleLike("unliked") }
        }
    }

    //
    // A request that answers writes only the fields it owns, onto whatever the entity says by
    // then. Writing back the post as it was read *before* the request was the bug this replaces.

    @Test
    fun `a toggleLike answer leaves fields that changed mid-flight alone`() = runTest {
        val dataSource = FakePostDataSource().apply { likeCounts["unliked"] = 4 }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        // A refresh landing while the like is on the wire, bringing a fresher post.
        dataSource.whileInFlight = {
            repo.ingest(listOf(unliked.copy(title = "Edited", commentCount = 9, isLiked = true, likeCount = 5)))
        }

        repo.toggleLike("unliked")

        val result = repo.entities.first()["unliked"]!!
        assertEquals("Edited", result.title)
        assertEquals(9, result.commentCount)
    }

    @Test
    fun `a failed toggleLike leaves fields that changed mid-flight alone`() = runTest {
        val dataSource = FakePostDataSource().apply { setLikeError = IllegalStateException("boom") }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        dataSource.whileInFlight = {
            repo.ingest(listOf(unliked.copy(title = "Edited", isLiked = true, likeCount = 5)))
        }

        runCatching { repo.toggleLike("unliked") }

        val result = repo.entities.first()["unliked"]!!
        assertEquals("Edited", result.title)
        assertFalse(result.isLiked)
    }

    // Two taps in flight at once: the second one is what the user last asked for, so the first
    // request's answer — which describes a state nobody is waiting for — must not overwrite it.
    @Test
    fun `an answer that a newer tap has moved past is dropped`() = runTest {
        val dataSource = FakePostDataSource().apply { likeCounts["unliked"] = 4 }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        dataSource.whileInFlight = {
            // Only the first request re-enters; the second must not recurse.
            dataSource.whileInFlight = null
            repo.toggleLike("unliked")
        }

        repo.toggleLike("unliked")

        // Both taps were sent, and the store is left on the second — the one the user last asked
        // for — rather than on the first request's later, and by now meaningless, answer.
        assertEquals(listOf("unliked" to true, "unliked" to false), dataSource.likeRequests)
        assertFalse(repo.entities.first()["unliked"]!!.isLiked)
    }

    @Test
    fun `a toggle only affects the targeted post`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked, liked))

        repo.toggleLike("unliked")

        assertEquals(liked, repo.entities.first()["liked"])
    }

    // The comment itself is the detail page's to hold; the count is a field on the post, so it
    // has to move in the shared store or the number under the card stays stale everywhere.
    @Test
    fun `commentAdded bumps the post's comment count`() = runTest {
        val repo = repository()
        repo.ingest(listOf(post(id = "p", commentCount = 2)))

        repo.commentAdded("p")

        assertEquals(3, repo.entities.first()["p"]!!.commentCount)
    }

    // Skipping is by instance, so every post that did not gain a comment must come back as the
    // same object — otherwise a comment recomposes every visible row.
    @Test
    fun `commentAdded leaves every other post the same instance`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked, liked))

        repo.commentAdded("unliked")

        assertSame(liked, repo.entities.first()["liked"])
    }

    @Test
    fun `commentAdded on an unknown id leaves the entities unchanged`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked))

        repo.commentAdded("missing")

        assertEquals(mapOf("unliked" to unliked), repo.entities.first())
    }

    @Test
    fun `delete removes the post from the entity map`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked, liked))

        repo.delete("unliked")

        assertEquals(mapOf("liked" to liked), repo.entities.first())
    }

    @Test
    fun `delete records the id as deleted`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked))

        repo.delete("unliked")

        assertEquals(setOf<PostId>("unliked"), repo.deletedIds.first())
    }

    // The store already knows the answer for an id it deleted, and "no such post" must hold
    // even offline — so no request is made.
    @Test
    fun `load answers a deleted id from the store without a request`() = runTest {
        val dataSource = FakePostDataSource()
        dataSource.fetchable["unliked"] = PostWithUsers(unliked, listOf(author))
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))
        repo.delete("unliked")

        assertNull(repo.load("unliked"))
        assertEquals(emptyList<PostId>(), dataSource.fetchedPostIds)
    }

    @Test
    fun `report goes through the data source`() = runTest {
        val dataSource = FakePostDataSource()
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        repo.report("unliked", ReportReason.SPAM, "Posted this five times")

        assertEquals(
            listOf(FakePostDataSource.Report("unliked", ReportReason.SPAM, "Posted this five times")),
            dataSource.reports,
        )
    }

    // A report says nothing about the post itself, so the card the reporter is looking at — and
    // every other screen showing it — must be left exactly as it was.
    @Test
    fun `report leaves the entity map untouched`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked))

        repo.report("unliked", ReportReason.SPAM, "")

        assertEquals(mapOf<PostId, Post>("unliked" to unliked), repo.entities.first())
    }

    // Unlike a toggle, a report needs no entity: an id is all it is about, so a post can be
    // reported from a screen that never resolved it into the store.
    @Test
    fun `report is made for a post the store does not hold`() = runTest {
        val dataSource = FakePostDataSource()
        val repo = repository(dataSource)

        repo.report("unknown", ReportReason.OTHER, "")

        assertEquals(listOf("unknown"), dataSource.reports.map { it.postId })
    }

    @Test
    fun `delete goes through the data source`() = runTest {
        val dataSource = FakePostDataSource()
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        repo.delete("unliked")

        assertEquals(listOf("unliked"), dataSource.deletedPostIds)
    }

    @Test
    fun `a failed delete keeps the post in the entity map`() = runTest {
        val dataSource = FakePostDataSource().apply { deleteError = IllegalStateException("boom") }
        val repo = repository(dataSource)
        repo.ingest(listOf(unliked))

        runCatching { repo.delete("unliked") }

        assertEquals(unliked, repo.entities.first()["unliked"])
    }

    @Test
    fun `toggling an unknown id leaves the entities unchanged`() = runTest {
        val repo = repository()
        repo.ingest(listOf(unliked))

        repo.toggleLike("missing")

        assertEquals(mapOf("unliked" to unliked), repo.entities.first())
    }
}

private fun post(
    id: String,
    likeCount: Int = 0,
    commentCount: Int = 0,
    isLiked: Boolean = false,
    isBookmarked: Boolean = false,
) = Post(
    id = id,
    url = testPostUrl(id),
    authorId = "u-$id",
    title = "Title $id",
    body = "Body $id",
    createdAt = Instant.EPOCH,
    likeCount = likeCount,
    commentCount = commentCount,
    isLiked = isLiked,
    isBookmarked = isBookmarked,
)
