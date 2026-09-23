package uno.lux.mosaic.common.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uno.lux.mosaic.common.R
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * A reusable confirmation dialog shown when a user attempts to navigate away from a screen
 * with unsaved changes.
 */
@Composable
fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_confirmation_title)) },
        text = { Text(stringResource(R.string.discard_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.discard_confirmation_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.discard_confirmation_dismiss))
            }
        },
    )
}

@Preview
@Composable
private fun DiscardChangesDialogPreview() {
    MosaicTheme {
        DiscardChangesDialog(onConfirm = {}, onDismiss = {})
    }
}
