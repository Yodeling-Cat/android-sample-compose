package uno.lux.mosaic.common.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uno.lux.mosaic.common.data.files.FileUpload

private val PlainText = "text/plain".toMediaType()

/** A file part under [name]. Use `"images[]"` for a part Rack should collect into an array. */
fun FileUpload.asPart(name: String): MultipartBody.Part = MultipartBody.Part.createFormData(
    name = name,
    filename = filename,
    body = bytes.toRequestBody(mimeType.toMediaType()),
)

/** Multipart cannot carry a JSON null, so an empty string clears a nullable field server-side. */
fun String.asTextPart(): RequestBody = toRequestBody(PlainText)
