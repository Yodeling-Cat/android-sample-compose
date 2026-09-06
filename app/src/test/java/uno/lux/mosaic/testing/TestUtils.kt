package uno.lux.mosaic.testing

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId

/**
 * The shareable link a test post carries. Real ones are built by the server and arrive as
 * [Post.url]; a test only needs a stable stand-in, so the shape lives here rather than being
 * spelled out at every construction site.
 *
 * The verbatim JSON fixtures are the deliberate exception — they stand in for bytes the server
 * sent, so they keep the URL written out in full alongside the rest of the payload.
 */
fun testPostUrl(id: PostId) = "https://mosaic.test/p/$id"
