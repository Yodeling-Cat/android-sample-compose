package uno.lux.mosaic.post.data.domain

import java.time.Instant

/**
 * Mirrors the server's `(createdAt, id)` descending keyset in `Cursor.paginate`; change both
 * together.
 */
data class PostKey(
    val createdAt: Instant,
    val id: PostId,
) : Comparable<PostKey> {
    override fun compareTo(other: PostKey): Int {
        val byRecency = createdAt.compareTo(other.createdAt)

        return if (byRecency != 0) byRecency else id.compareTo(other.id)
    }
}

val Post.key: PostKey get() = PostKey(createdAt, id)
