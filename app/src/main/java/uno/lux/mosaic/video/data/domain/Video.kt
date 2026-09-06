package uno.lux.mosaic.video.data.domain

typealias VideoId = String

/**
 * A post's video attachment.
 *
 * The four pixel dimensions are the server's own measurements: [width]/[height] describe the
 * stored clip, [thumbnailWidth]/[thumbnailHeight] the poster frame it extracted from the middle of
 * it. All of them — and [thumbnailUrl] itself — are null when the server could not read the file
 * (a clip publishes regardless, without a thumbnail), so they are a *hint* to size a request or a
 * slot with, never a guarantee. [durationSeconds] is the exception: it is always sent, reading 0
 * rather than null when unknown.
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
