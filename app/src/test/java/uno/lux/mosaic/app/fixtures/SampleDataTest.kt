package uno.lux.mosaic.app.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {

    @Test
    fun `each sample video streams from a url of its own`() {
        val videoUrls = SamplePosts.mapNotNull { it.video?.videoUrl }

        assertTrue("expected at least two video posts", videoUrls.size >= 2)
        // The shared player keys playback on the URL, so a repeated one would make two distinct
        // videos the same playback — they could not then be played independently.
        assertEquals("sample videos should not share a stream", videoUrls.distinct(), videoUrls)
    }

    @Test
    fun `sample albums each show photos of their own`() {
        val albums = SamplePosts.mapNotNull { it.album }
        val images = albums.flatMap { it.images }

        assertTrue("expected at least two album posts", albums.size >= 2)
        assertEquals("sample albums should not share a photo", images.distinct(), images)
    }

    // The number on the card and the thread the card opens are the same fixtures, so a post
    // claiming 612 comments over a list of two was a preview contradicting itself. The server
    // counts its comment rows for the same reason; these stand in for what it sends.
    @Test
    fun `a sample post counts the comments it has`() {
        assertTrue("expected sample comments", SampleComments.isNotEmpty())
        SamplePosts.forEach { post ->
            assertEquals(
                "post ${post.id} should count its own comments",
                SampleComments[post.id].orEmpty().size,
                post.commentCount,
            )
        }
    }

    @Test
    fun `a sample album counts the images it carries`() {
        val albums = SamplePosts.mapNotNull { it.album }

        assertTrue("expected at least one album post", albums.isNotEmpty())
        albums.forEach { album ->
            assertEquals(
                "album ${album.id} should count its own images",
                album.images.size,
                album.itemCount,
            )
        }
    }
}
