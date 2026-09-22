package uno.lux.mosaic.common.data.files

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads a picked file URI into a [FileUpload] the data layer can upload. Kept as an
 * interface so ViewModels can depend on it without touching Android
 * `ContentResolver` types, and so tests can supply a fake.
 */
interface FileLoader {
    /** Reads the file at [uri] (a `content://` / `file://` URI string) into an upload payload. */
    suspend fun read(uri: String): FileUpload

    /**
     * Size of the file at [uri] in bytes, or `null` when the provider doesn't report one. Exists so
     * a size limit can be enforced *without* [read]ing the file into memory first — the point of a
     * limit is to avoid holding an oversized video, which reading it would already have done.
     */
    suspend fun sizeOf(uri: String): Long?
}

/** Android implementation backed by [Context.getContentResolver]. */
class AndroidFileLoader(
    private val context: Context,
) : FileLoader {

    override suspend fun read(uri: String): FileUpload = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        val resolver = context.contentResolver

        val mimeType = resolver.getType(parsed) ?: DEFAULT_MIME_TYPE
        val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() }
            ?: throw IOException("Unable to open file at $uri")
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"

        FileUpload(bytes = bytes, mimeType = mimeType, filename = "upload.$extension")
    }

    override suspend fun sizeOf(uri: String): Long? = withContext(Dispatchers.IO) {
        context.contentResolver
            .openAssetFileDescriptor(uri.toUri(), "r")
            ?.use { it.length.takeIf { length -> length != AssetFileDescriptor.UNKNOWN_LENGTH } }
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
