package uno.lux.mosaic.comment.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.common.data.network.LikeStateDto
import uno.lux.mosaic.common.data.network.SetLikeRequestDto

class NetworkCommentDataSourceTest {

    @Test
    fun `loadComments fetches from the API`() = runTest {
        val api = FakeCommentApi(comments = listOf(commentDto("c1", "Hello")))
        val dataSource = NetworkCommentDataSource(api)

        val loaded = dataSource.loadComments("p1", cursor = null).comments

        assertEquals(1, loaded.size)
        assertEquals("c1", loaded.single().id)
        assertEquals("Hello", loaded.single().text)
    }

    @Test
    fun `loadComments returns empty list when the API returns none`() = runTest {
        val dataSource = NetworkCommentDataSource(FakeCommentApi(comments = emptyList()))

        val loaded = dataSource.loadComments("p1", cursor = null)

        assertTrue(loaded.comments.isEmpty())
    }

    // The token is the server's to mint and the ViewModel's to hand back; this layer only carries
    // it, so a page dropping it would end a thread that has more to show.
    @Test
    fun `loadComments carries the page's cursor and hasMore`() = runTest {
        val api = FakeCommentApi(page = CursorPageDto(nextCursor = "c-20", hasMore = true))
        val dataSource = NetworkCommentDataSource(api)

        val page = dataSource.loadComments("p1", cursor = null)

        assertEquals("c-20", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `loadComments asks for the page the cursor names`() = runTest {
        val api = FakeCommentApi()
        val dataSource = NetworkCommentDataSource(api)

        dataSource.loadComments("p1", cursor = null)
        dataSource.loadComments("p1", cursor = "c-20")

        assertEquals(listOf(null, "c-20"), api.commentCursors)
    }

    @Test
    fun `addComment returns the server-created comment`() = runTest {
        val dataSource = NetworkCommentDataSource(FakeCommentApi())

        val comment = dataSource.addComment("p1", "New thought")

        assertEquals("c-new", comment.id)
        assertEquals("New thought", comment.text)
    }

    @Test
    fun `setLike answers with the isLiked and likeCount the server settled on`() = runTest {
        val api = FakeCommentApi(likeResult = LikeStateDto(isLiked = true, likeCount = 3))
        val dataSource = NetworkCommentDataSource(api)

        val settled = dataSource.setLike("p1", "c1", liked = true)

        assertEquals(LikeState(isLiked = true, likeCount = 3), settled)
    }

    // The wire carries the state being asked for rather than a request to flip whatever is there,
    // so a request the server sees twice moves the like once.
    @Test
    fun `setLike sends the state the caller asked for`() = runTest {
        val api = FakeCommentApi()
        val dataSource = NetworkCommentDataSource(api)

        dataSource.setLike("p1", "c1", liked = false)

        assertEquals(listOf("c1" to SetLikeRequestDto(liked = false)), api.likeRequests)
    }
}
