package uno.lux.mosaic.user.domain

import uno.lux.mosaic.common.data.files.FileUpload

data class ProfileUpdate(
    val nickname: String,
    val age: Int?,
    val gender: Gender?,
    val bio: String?,
    val avatar: FileUpload?,
)
