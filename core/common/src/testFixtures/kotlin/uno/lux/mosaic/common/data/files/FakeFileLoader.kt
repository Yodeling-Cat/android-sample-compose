package uno.lux.mosaic.common.data.files

/**
 * Shared [FileLoader] double. [read] records the URI it was asked for and returns a payload naming
 * it, so a test can assert *which* files were uploaded; [error] and [size] drive the failure and
 * size-limit paths.
 */
class FakeFileLoader(
    private val result: FileUpload? = null,
    private val error: Exception? = null,
    private val size: Long? = 1_000,
) : FileLoader {

    var lastUri: String? = null
        private set

    override suspend fun read(uri: String): FileUpload {
        lastUri = uri
        error?.let { throw it }

        // Naming the payload after the URI is what lets a test match uploads to picks.
        return result
            ?: FileUpload(bytes = uri.toByteArray(), mimeType = "image/png", filename = uri)
    }

    override suspend fun sizeOf(uri: String): Long? = size
}

/** Reports a fixed duration for any video URI. */
class FakeVideoMetadataReader(
    private val duration: Int = 12,
) : VideoMetadataReader {
    override suspend fun durationSeconds(uri: String): Int = duration
}
