package uno.lux.mosaic.post.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uno.lux.mosaic.R
import uno.lux.mosaic.app.theme.MosaicTheme

/** Confirms deleting a post. Deletion is irreversible. */
@Composable
internal fun DeletePostDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.post_delete_confirmation_title)) },
        text = { Text(stringResource(R.string.post_delete_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.post_delete_confirmation_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.post_delete_confirmation_dismiss))
            }
        },
    )
}

@Preview
@Composable
private fun DeletePostDialogPreview() {
    MosaicTheme {
        DeletePostDialog(
            onConfirm = {},
            onDismiss = {},
        )
    }
}
