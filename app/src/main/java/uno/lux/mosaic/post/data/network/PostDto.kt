package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.InstantSerializer
import java.time.Instant

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
