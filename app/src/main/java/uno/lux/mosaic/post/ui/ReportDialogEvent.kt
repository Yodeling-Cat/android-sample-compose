package uno.lux.mosaic.post.ui

import uno.lux.mosaic.common.data.ReportReason

sealed interface ReportDialogEvent {

    data class Send(
        val reason: ReportReason,
        val details: String,
    ) : ReportDialogEvent

    data object Close : ReportDialogEvent

    data object SentShown : ReportDialogEvent
}
