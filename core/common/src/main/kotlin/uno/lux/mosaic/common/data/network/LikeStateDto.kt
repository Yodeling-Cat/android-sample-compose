package uno.lux.mosaic.common.data.network

import kotlinx.serialization.Serializable

/**
 * Asks for a like to be *in* a state rather than to be flipped. Posts and comments both take it.
 *
 * The endpoint used to be a bodiless toggle, which made it non-idempotent: a retry after a
 * timeout, or two taps the client sent before either answered, moved the like twice. Naming the
 * state the tap wants makes a repeat harmless — the second request finds the like already there
 * and answers the same thing.
 */
@Serializable
data class SetLikeRequestDto(
    val liked: Boolean,
)

/**
 * The answer to liking *something*. Posts and comments both have a heart and both reply with
 * this exact envelope, so it belongs to neither aggregate — bookmarking, which only posts do,
 * stays in `post`.
 */
@Serializable
data class LikeStateDto(
    val isLiked: Boolean,
    val likeCount: Int,
)
