package uno.lux.mosaic.common.data

/**
 * The like fields of *something* as the server holds them: whether the viewer likes it, and how
 * many likes it has. Posts and comments both have a heart and both answer with these two fields,
 * so — like the [uno.lux.mosaic.common.data.network.LikeStateDto] it is mapped from — it belongs
 * to neither aggregate.
 *
 * The two travel together because a like moves both, and they arrive as their own value rather
 * than as a whole post or comment because they are the *only* fields a like request may write
 * back. A request that answered with an entity would carry a copy of every other field as the
 * server saw it when the request went out, and writing that copy back would undo anything that
 * landed meanwhile.
 */
data class LikeState(
    val isLiked: Boolean,
    val likeCount: Int,
)
