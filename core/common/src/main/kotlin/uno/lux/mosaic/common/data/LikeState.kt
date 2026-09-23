package uno.lux.mosaic.common.data

/**
 * The like fields of a post or comment as the server holds them: whether the viewer likes it, and
 * how many likes it has.
 *
 * A like answers with these two fields rather than the whole entity because they are the only ones
 * it may write back. A whole entity would carry a stale copy of every other field, and writing it
 * back would undo anything that landed meanwhile.
 */
data class LikeState(
    val isLiked: Boolean,
    val likeCount: Int,
)
