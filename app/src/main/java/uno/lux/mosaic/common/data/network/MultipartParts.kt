package uno.lux.mosaic.common.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uno.lux.mosaic.common.data.files.FileUpload

/**
 * How a [FileUpload] and a plain text field become `multipart/form-data` parts. Both upload
 * endpoints — the avatar on `PATCH /users/{id}` and the media on `POST /posts` — send the same
 * shapes, so the encoding lives here rather than in each data source: changing how a file becomes
 * a part (streaming from the URI instead of a byte array, say) is then one edit, not two.
 *
 * It sits in `data/network` and not beside [FileUpload] in `data/files` because it is the OkHttp
 * half of the pair: `files` reads the platform and knows no transport, this names the wire.
 */

private val PlainText = "text/plain".toMediaType()

/** A file part under [name]. Use `"images[]"` for a part Rack should collect into an array. */
fun FileUpload.asPart(name: String): MultipartBody.Part = MultipartBody.Part.createFormData(
    name = name,
    filename = filename,
    body = bytes.toRequestBody(mimeType.toMediaType()),
)

/**
 * A text field's value. Multipart can't carry a JSON null, so an empty string is what clears a
 * nullable field server-side.
 */
fun String.asTextPart(): RequestBody = toRequestBody(PlainText)
