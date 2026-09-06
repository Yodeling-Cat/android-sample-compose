package uno.lux.mosaic.post.data.domain

import java.time.Instant

/**
 * A post's place in the API's ordering: `(createdAt, id)` **descending**, the keyset every
 * paginated list here is sorted and cursored by. The server's `Cursor.paginate` is the other half
 * of this contract — the two orderings have to agree, so change them together.
 *
 * [Post] carries both fields, which is what lets the client put a post in that order itself rather
 * than only ever echoing back the order it was handed: a locally-derived list (see
 * `ProfileRepository`'s Saved and Likes tabs) can place a newly flagged post exactly where a
 * refetch would have put it.
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
