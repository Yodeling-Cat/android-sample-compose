package uno.lux.mosaic.post.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.ReportReason

/**
 * The body of a report: `{ "reason": "hate_speech", "details"?: "…" }`.
 *
 * [details] defaults to null and the app's `Json` does not encode defaults, so a report with
 * nothing typed in the optional field leaves the key off the wire entirely rather than sending
 * an empty string the server would have to treat as absent anyway.
 */
@Serializable
data class ReportPostRequestDto(
    val reason: ReportReasonDto,
    val details: String? = null,
)

/**
 * A [ReportReason] as the server spells it (`PostsService::REPORT_REASONS`).
 *
 * Kept apart from the domain enum so the wire spelling is stated where the wire types live, and
 * so neither side's naming constrains the other's. [toDto]'s `when` is exhaustive, so a reason
 * added to [ReportReason] without a spelling here fails to compile rather than at the server.
 */
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
