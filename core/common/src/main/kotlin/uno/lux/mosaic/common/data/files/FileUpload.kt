package uno.lux.mosaic.common.data.files

class FileUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val filename: String,
) {
    // Value semantics over the bytes, so a payload carrying a file compares structurally
    // (used by tests and to dedupe identical requests).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileUpload) return false

        return mimeType == other.mimeType &&
            filename == other.filename &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + filename.hashCode()
        return result
    }
}
