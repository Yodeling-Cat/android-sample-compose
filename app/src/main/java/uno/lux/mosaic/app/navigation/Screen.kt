package uno.lux.mosaic.app.navigation

import kotlinx.serialization.Serializable
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video

@Serializable
sealed interface Screen {

    /**
     * `null` gives each push its own state; a value makes pushes share one. Declare it as `get() =
     * …`: a backing field would be serialized into the key.
     */
    val sharedId: String? get() = null

    @Serializable
    data object Shell : Screen {
        override val sharedId: String get() = "shell"
    }

    @Serializable
    data class Profile(
        val userId: UserId,
    ) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object EditProfile : Screen

    @Serializable
    data object CreatePost : Screen

    @Serializable
    data class FullscreenVideo(
        val url: String,
        val title: String? = null,
    ) : Screen {
        constructor(video: Video) : this(video.videoUrl, video.title)
    }

    @Serializable
    data class PostDetail(
        val postId: PostId,
    ) : Screen

    @Serializable
    data class AlbumViewer(
        val images: List<String>,
        val initialIndex: Int,
    ) : Screen
}
