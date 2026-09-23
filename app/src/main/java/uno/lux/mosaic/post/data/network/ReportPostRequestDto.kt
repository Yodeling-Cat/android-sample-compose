package uno.lux.mosaic.post.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.ReportReason

@Serializable
data class ReportPostRequestDto(
    val reason: ReportReasonDto,
    val details: String? = null,
)

/** A [ReportReason] as the server spells it (`PostsService::REPORT_REASONS`). */
@Serializable
enum class ReportReasonDto {
    @SerialName("spam")
    SPAM,

    @SerialName("harassment")
    HARASSMENT,

    @SerialName("hate_speech")
    HATE_SPEECH,

    @SerialName("misinformation")
    MISINFORMATION,

    @SerialName("violence")
    VIOLENCE,

    @SerialName("other")
    OTHER,
}

fun ReportReason.toDto(): ReportReasonDto = when (this) {
    ReportReason.SPAM -> ReportReasonDto.SPAM
    ReportReason.HARASSMENT -> ReportReasonDto.HARASSMENT
    ReportReason.HATE_SPEECH -> ReportReasonDto.HATE_SPEECH
    ReportReason.MISINFORMATION -> ReportReasonDto.MISINFORMATION
    ReportReason.VIOLENCE -> ReportReasonDto.VIOLENCE
    ReportReason.OTHER -> ReportReasonDto.OTHER
}
