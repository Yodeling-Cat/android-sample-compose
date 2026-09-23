package uno.lux.mosaic.common.data.files

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface VideoMetadataReader {
    /** Whole-second duration of the video at [uri], or 0 when it can't be determined. */
    suspend fun durationSeconds(uri: String): Int
}

class AndroidVideoMetadataReader(
    private val context: Context,
) : VideoMetadataReader {

    override suspend fun durationSeconds(uri: String): Int = withContext(Dispatchers.IO) {
        // Not `use`: MediaMetadataRetriever only became AutoCloseable in API 29 and minSdk is 26.
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri.toUri())
            val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            // Round to the nearest second: a 4.6s clip reading as "4s" looks like a bug.
            millis?.toLongOrNull()?.let { ms -> ((ms + 500) / 1000).toInt() } ?: 0
        } catch (e: RuntimeException) {
            // A DRM-protected or malformed file throws rather than returning null. Duration is
            // cosmetic, so this degrades to 0 rather than blocking the post.
            Timber.w(e, "Could not read duration of %s", uri)
            0
        } finally {
            retriever.release()
        }
    }
}
