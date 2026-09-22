package uno.lux.mosaic.user.data.domain

import uno.lux.mosaic.common.data.files.FileUpload

data class ProfileUpdate(
    val nickname: String,
    val age: Int?,
    val gender: String?, // TODO: backend accepts ("Man" / "Woman"), lets make it an enum
    val bio: String?,
    val avatar: FileUpload?,
)
