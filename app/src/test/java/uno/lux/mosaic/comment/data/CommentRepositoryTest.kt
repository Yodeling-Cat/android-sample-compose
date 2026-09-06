package uno.lux.mosaic.comment.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

class CommentRepositoryTest {

    private val author = User(id = "u1", nickname = "Ada", handle = "@ada")

    private fun comment(
        id: String,
        text: String = "A comment",
        likeCount: Int = 0,
        isLiked: Boolean = false,
    ) = Comment(
        id = id,
        author = author,
        createdAt = Instant.EPOCH,
        text = text,
        likeCount = likeCount,
        isLiked = isLiked,
    )

    @Test
    fun `loadComments returns empty list for unknown post`() = runTest {
        val repository = CommentRepository(FakeCommentDataSource(author))

        val page = repository.loadComments("no-such-post", cursor = null)

        assertEquals(emptyList<Comment>(), page.comments)
        assertFalse(page.hasMore)
    }

    @Test
    fun `loadComments returns the seeded list for a known post`() = runTest {
        val c = comment("c1")
        val repository = CommentRepository(
            FakeCommentDataSource(
                author,
                comments = mapOf(
                    "p1" to listOf(c),
                ),
            ),
        )

        val page = repository.loadComments("p1", cursor = null)

        assertEquals(listOf(c), page.comments)
    }

    // The repository carries the cursor rather than remembering it: the window belongs to the
    // screen reading it, so asking for the page after one is the caller's move to make.
    @Test
    fun `loadComments serves the page the cursor names`() = runTest {
        val thread = listOf(comment("c1"), comment("c2"), comment("c3"))
        val dataSource = FakeCommentDataSource(author, mapOf("p1" to thread), pageSize = 2)
        val repository = CommentRepository(dataSource)

        val first = repository.loadComments("p1", cursor = null)
        val second = repository.loadComments("p1", cursor = first.nextCursor)

        assertEquals(listOf(thread[0], thread[1]), first.comments)
        assertTrue(first.hasMore)
        assertEquals(listOf(thread[2]), second.comments)
        assertFalse(second.hasMore)
        assertNull(second.nextCursor)
    }

    @Test
    fun `addComment returns a new comment authored by the current user`() = runTest {
        val repository = CommentRepository(FakeCommentDataSource(author))

        val added = repository.addComment("p1", "New thought")

        assertEquals("New thought", added.text)
        assertEquals(author, added.author)
        assertEquals(0, added.likeCount)
        assertFalse(added.isLiked)
    }

    @Test
    fun `setLike asks the data source for the state and answers with the server's`() = runTest {
        val dataSource = FakeCommentDataSource(author).apply { likeCounts["c1"] = 3 }
        val repository = CommentRepository(dataSource)

        val result = repository.setLike("p1", "c1", liked = true)

        assertEquals(listOf("c1" to true), dataSource.likeRequests)
        assertTrue(result.isLiked)
        assertEquals(4, result.likeCount)
    }

    // Unliking is the same call with the other state, not a second operation — which is what lets
    // a retry of either repeat a state rather than repeat a flip.
    @Test
    fun `setLike asks for false the same way it asks for true`() = runTest {
        val dataSource = FakeCommentDataSource(author).apply { likeCounts["c1"] = 7 }
        val repository = CommentRepository(dataSource)

        val result = repository.setLike("p1", "c1", liked = false)

        assertEquals(listOf("c1" to false), dataSource.likeRequests)
        assertFalse(result.isLiked)
        assertEquals(6, result.likeCount)
    }
}
