package uno.lux.mosaic.post.ui

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User

/**
 * Resolved display data for a single post card: the post and its resolved author.
 *
 * [isOwn] is the post being authored by the signed-in user — the one card-level fact that depends
 * on *who is looking* rather than on the post, which is why the ViewModel resolves it once here
 * instead of every card comparing IDs. It gates the overflow menu's delete action.
 */
data class PostCardData(
    val post: Post,
    val author: User,
    val isOwn: Boolean = false,
)
