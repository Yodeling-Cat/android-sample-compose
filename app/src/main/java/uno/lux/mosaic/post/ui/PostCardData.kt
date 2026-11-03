package uno.lux.mosaic.post.ui

import uno.lux.mosaic.post.domain.Post
import uno.lux.mosaic.user.domain.User

data class PostCardData(
    val post: Post,
    val author: User,
    val isOwn: Boolean = false,
)
