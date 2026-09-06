package uno.lux.mosaic.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uno.lux.mosaic.R
import uno.lux.mosaic.app.theme.LocalMosaicColors
import uno.lux.mosaic.app.theme.MosaicTheme
import uno.lux.mosaic.app.theme.accentBarColors
import uno.lux.mosaic.app.ui.components.AppBarAction
import uno.lux.mosaic.app.util.LightStatusBarIcons
import uno.lux.mosaic.settings.data.domain.AppLanguage
import uno.lux.mosaic.settings.data.domain.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val eventSink = viewModel::eventSink
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        eventSink = eventSink,
        state = state,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    eventSink: (SettingsUiEvent) -> Unit,
    state: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    LightStatusBarIcons()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                modifier = Modifier.shadow(4.dp),
                colors = accentBarColors(),
                navigationIcon = {
                    AppBarAction(
                        icon = R.drawable.ic_arrow_back,
                        onClick = { eventSink(SettingsUiEvent.GoBack) },
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                },
            )
        },
    ) { contentPadding ->
        when (state) {
            SettingsUiState.Loading -> Unit

            is SettingsUiState.Content -> SettingsScreenContent(
                eventSink = eventSink,
                state = state,
                modifier = Modifier
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun SettingsScreenContent(
    eventSink: (SettingsUiEvent) -> Unit,
    state: SettingsUiState.Content,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(title = stringResource(R.string.settings_language)) {
            SingleChoiceRow(
                options = AppLanguage.entries,
                selected = state.language,
                onSelected = { eventSink(SettingsUiEvent.SetLanguage(it)) },
                label = { stringResource(it.labelRes()) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_appearance)) {
            SingleChoiceRow(
                options = ThemeMode.entries,
                selected = state.themeMode,
                onSelected = { eventSink(SettingsUiEvent.SetThemeMode(it)) },
                label = { stringResource(it.labelRes()) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_preferences)) {
            SwitchRow(
                label = stringResource(R.string.settings_autoplay_videos),
                supportingText = stringResource(R.string.settings_autoplay_videos_description),
                checked = state.autoPlayVideos,
                onCheckedChange = { eventSink(SettingsUiEvent.SetAutoPlayVideos(it)) },
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/**
 * An on/off setting: its [label] and [supportingText] beside a trailing switch. The whole row is
 * one `toggleable` target — the switch itself takes no click, so a tap anywhere on the row flips it
 * and the accessibility tree sees a single [Role.Switch] node rather than a label and a control.
 */
@Composable
private fun SwitchRow(
    label: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = LocalMosaicColors.current.textTertiary,
            )
        }

        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A segmented row over [options], one selectable button each — the shared shape of every picker on
 * this screen. Generic over the option type, so a picker only supplies its options and their labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SingleChoiceRow(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(text = label(option), maxLines = 1)
            }
        }
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.CZECH -> R.string.language_czech
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MosaicTheme {
        SettingsScreen(
            eventSink = {},
            state = SettingsUiState.Content(
                themeMode = ThemeMode.SYSTEM,
                autoPlayVideos = true,
                language = AppLanguage.ENGLISH,
            ),
        )
    }
}
