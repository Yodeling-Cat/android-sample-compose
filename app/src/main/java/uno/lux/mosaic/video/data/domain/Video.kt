package uno.lux.mosaic.video.data.domain

typealias VideoId = String

/**
 * The dimensions and [thumbnailUrl] are null when the server could not read the file, so treat them
 * as hints. [durationSeconds] reads 0 rather than null when unknown.
 */
data class Video(
    val id: VideoId,
    val title: String,
    val durationSeconds: Int,
    val videoUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailUrl: String? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
)
