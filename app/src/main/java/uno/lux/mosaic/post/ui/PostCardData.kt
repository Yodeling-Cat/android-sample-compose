package uno.lux.mosaic.post.ui

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User

data class PostCardData(
    val post: Post,
    val author: User,
    val isOwn: Boolean = false,
)
