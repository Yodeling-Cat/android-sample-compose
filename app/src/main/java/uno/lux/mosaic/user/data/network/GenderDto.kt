package uno.lux.mosaic.user.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uno.lux.mosaic.user.data.domain.Gender

/** A [Gender] as the server spells it (the `inclusion` validation on `User#gender`). */
@Serializable
enum class GenderDto {
    @SerialName("Man")
    MAN,

    @SerialName("Woman")
    WOMAN,
}

/** The [SerialName], for a multipart text part, which JSON encoding cannot reach. */
val GenderDto.wireName: String
    get() = GenderDto.serializer().descriptor.getElementName(ordinal)

fun Gender.toDto(): GenderDto = when (this) {
    Gender.MAN -> GenderDto.MAN
    Gender.WOMAN -> GenderDto.WOMAN
}
