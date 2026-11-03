package uno.lux.mosaic.comment.domain

import uno.lux.mosaic.user.domain.User
import java.time.Instant

typealias CommentId = String

data class Comment(
    val id: CommentId,
    val author: User,
    val createdAt: Instant,
    val text: String,
    val likeCount: Int,
    val isLiked: Boolean = false,
)
