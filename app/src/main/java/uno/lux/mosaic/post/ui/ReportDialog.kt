package uno.lux.mosaic.post.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.R
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * Mirrors the server's `PostsService::REPORT_DETAILS_MAX_LENGTH`. The report is acknowledged the
 * moment it is sent, so a report the server would refuse must not be typeable in the first place.
 */
const val REPORT_DETAILS_MAX_LENGTH = 1000

/**
 * The report-a-post dialog: a single-choice list of [ReportReason]s plus an optional free-text
 * field. The chosen reason and trimmed details are handed to [onSubmit]; Send stays disabled
 * until a reason is picked. The selection is owned here, so the host deals only with the submit
 * and dismiss outcomes.
 *
 * The host closes this dialog on [ReportSendState.SENT] and not before, so the reason the user
 * picked is still there to send again after a [ReportSendState.FAILED]. Only Send is taken away
 * while a report is on the wire — Cancel keeps working, so a stalled request cannot trap the
 * user in a dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPostDialog(
    sendState: ReportSendState,
    onDismiss: () -> Unit,
    onSubmit: (reason: ReportReason, details: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable like the details text below it: rotating mid-report must not drop the choice.
    var selectedReason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    val detailsState = rememberTextFieldState()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DialogSurface {
            ReportPostDialogContent(
                selectedReason = selectedReason,
                detailsState = detailsState,
                sendState = sendState,
                onSelectReason = { selectedReason = it },
                onDismiss = onDismiss,
                onSubmit = {
                    onSubmit(selectedReason!!, detailsState.text.toString().trim())
                },
            )
        }
    }
}

@Composable
private fun ReportPostDialogContent(
    selectedReason: ReportReason?,
    detailsState: TextFieldState,
    sendState: ReportSendState,
    onSelectReason: (ReportReason) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val isSending = sendState == ReportSendState.SENDING

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = stringResource(R.string.report_dialog_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        // The title and buttons are measured first and the scroll area takes what is left, so a
        // long details field scrolls instead of pushing Send out of the dialog. `fill = false`
        // keeps a dialog with nothing typed in it as short as its content.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.report_dialog_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ReportReason.entries.forEach { reason ->
                ReasonRow(
                    label = reason.asText(),
                    selected = reason == selectedReason,
                    onSelect = { onSelectReason(reason) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                state = detailsState,
                label = { Text(stringResource(R.string.report_details_hint)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
                inputTransformation = InputTransformation.maxLength(REPORT_DETAILS_MAX_LENGTH),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Outside the scroll area, so a failure is stated right over the button they tapped.
        if (sendState == ReportSendState.FAILED) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.report_send_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Beside the buttons rather than inside Send, so a report going out doesn't resize it.
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.report_cancel))
            }
            TextButton(
                onClick = onSubmit,
                enabled = selectedReason != null && !isSending,
            ) {
                Text(stringResource(R.string.report_send))
            }
        }
    }
}

/** One selectable reason; the whole row is the radio target so the tap area isn't just the dot. */
@Composable
private fun ReasonRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ReportPostDialogPreview() {
    ReportPostDialogPreview(selectedReason = null, sendState = ReportSendState.IDLE)
}

@Preview(showBackground = true)
@Composable
private fun ReportPostDialogSendingPreview() {
    ReportPostDialogPreview(
        selectedReason = ReportReason.HARASSMENT,
        sendState = ReportSendState.SENDING,
    )
}

@Preview(showBackground = true)
@Composable
private fun ReportPostDialogFailedPreview() {
    ReportPostDialogPreview(
        selectedReason = ReportReason.HARASSMENT,
        sendState = ReportSendState.FAILED,
    )
}

@Composable
private fun ReportPostDialogPreview(selectedReason: ReportReason?, sendState: ReportSendState) {
    MosaicTheme {
        DialogSurface {
            ReportPostDialogContent(
                selectedReason = selectedReason,
                detailsState = rememberTextFieldState(),
                sendState = sendState,
                onSelectReason = {},
                onDismiss = {},
                onSubmit = {},
            )
        }
    }
}
