package uno.lux.mosaic.post.data.domain

import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video
import java.time.Instant

typealias PostId = String

data class Post(
    val id: PostId,
    val url: String,
    val authorId: UserId,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val video: Video? = null,
    val album: Album? = null,
)

data class PostWithUsers(
    val post: Post,
    val users: List<User>,
)
