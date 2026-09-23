package uno.lux.mosaic.common.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.designsystem.theme.MosaicElevations
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/**
 * One group of a form, lifted onto a card so the page tint behind it reads as ground rather than
 * as an unfinished screen. A form's groups are the only structure it has, so the cards carry that
 * structure and no heading has to state it.
 *
 * Outlined rather than shadowed, because a form is a *stack* of these: a stack of borders reads
 * as a list, where a stack of shadows reads as clutter.
 */
@Composable
fun FormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = MosaicElevations.Card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormCardPreview() {
    MosaicTheme {
        FormCard(Modifier.padding(16.dp)) {
            Text("Nickname", style = MaterialTheme.typography.titleSmall)
            Text("Ada Lovelace", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
