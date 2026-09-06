package uno.lux.mosaic.comment.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.InstantSerializer
import uno.lux.mosaic.user.data.network.UserDto
import java.time.Instant

@Serializable
data class CommentDto(
    val id: String,
    val text: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val likeCount: Int,
    val isLiked: Boolean,
    val author: UserDto,
)

@Serializable
data class AddCommentRequestDto(
    val text: String,
)
