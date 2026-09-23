package uno.lux.mosaic.common.data

/**
 * The only fields a like may write back: a whole entity would carry a stale copy of every other
 * field.
 */
data class LikeState(
    val isLiked: Boolean,
    val likeCount: Int,
)
