package uno.lux.mosaic.user.data.domain

import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.user.data.UserRepository

/**
 * The editable slice of a [User]'s profile, applied atomically by
 * [UserRepository.updateProfile]. `null` clears an optional text field; [gender] carries one
 * of the canonical values the backend accepts ("Man" / "Woman"). [avatar] is a newly chosen
 * image to upload, or `null` to leave the current avatar untouched.
 */
data class ProfileUpdate(
    val nickname: String,
    val age: Int?,
    val gender: String?,
    val bio: String?,
    val avatar: FileUpload?,
)
