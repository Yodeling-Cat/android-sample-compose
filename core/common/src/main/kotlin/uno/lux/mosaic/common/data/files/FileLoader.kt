package uno.lux.mosaic.common.data.files

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface FileLoader {
    suspend fun read(uri: String): FileUpload

    /** `null` when the provider reports no size. */
    suspend fun sizeOf(uri: String): Long?
}

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
