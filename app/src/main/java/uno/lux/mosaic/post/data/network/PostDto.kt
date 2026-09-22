package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.InstantSerializer
import java.time.Instant

/**
 * The server's one post projection, served by every endpoint that answers with a post. It names
 * its author by ID rather than embedding the user: the endpoints returning a *single* post
 * sideload that author under `included` the way a page of posts does, so there is one shape to
 * read rather than two that differ only in where the author turns up.
 */
@Serializable
data class PostDto(
    val id: String,
    val url: String,
    val title: String,
    val body: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val authorId: String,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,
    val isBookmarked: Boolean,
    val album: AlbumDto? = null,
    val video: VideoDto? = null,
)

@Serializable
data class AlbumDto(
    val id: String,
    val title: String,
    val itemCount: Int,
    val images: List<String>,
)

@Serializable
data class VideoDto(
    val id: String,
    val title: String,
    val durationSeconds: Int,
    val videoUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailUrl: String? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
)
