package uno.lux.mosaic.user.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.app.theme.MosaicTheme
import uno.lux.mosaic.app.theme.accentBarColors
import uno.lux.mosaic.app.theme.rememberAccentWash
import uno.lux.mosaic.app.ui.components.AppBarAction
import uno.lux.mosaic.app.util.LightStatusBarIcons
import uno.lux.mosaic.app.util.createActionsProxy
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.ui.DiscardChangesDialog
import uno.lux.mosaic.common.ui.FormCard
import uno.lux.mosaic.common.ui.FullScreenError
import uno.lux.mosaic.common.ui.FullScreenProgress
import uno.lux.mosaic.user.data.domain.UserId

/**
 * The editor's ViewModel-backed intents as one [Stable] seam the stateless
 * [EditProfileScreen] depends on — including navigating back, which the ViewModel forwards to
 * the injected `Navigator` (and triggers itself once a save lands). [EditProfileViewModel]
 * implements it, so the binder passes the ViewModel directly and a preview passes a no-op
 * [createActionsProxy]. Only the photo picker launch stays a separate lambda — an
 * Activity-result API is the platform's concern.
 */
@Stable
interface EditProfileActions {
    fun onNicknameChange(value: String)

    fun onAgeChange(value: String)

    fun onGenderChange(gender: GenderOption)

    fun onBioChange(value: String)

    fun onAvatarChange(uri: String)

    fun save()

    fun retry()

    fun goBack()

    fun dismissDiscardConfirmation()

    fun confirmDiscard()
}

/**
 * Stateful entry point: binds the [EditProfileViewModel] and owns the photo-picker launcher
 * (an Activity-result API, so it can't live in the ViewModel).
 */
@Composable
fun EditProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // The picker's session-scoped read grant is enough — the image bytes are read and
        // uploaded on save, so no persistable permission is needed.
        if (uri != null) viewModel.onAvatarChange(uri.toString())
    }

    BackHandler {
        viewModel.goBack()
    }

    EditProfileScreen(
        uiState = uiState,
        actions = viewModel,
        onPickAvatar = {
            pickAvatar.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        modifier = modifier,
    )

    if (uiState is EditProfileUiState.Editing &&
        (uiState as EditProfileUiState.Editing).showDiscardConfirmation
    ) {
        DiscardChangesDialog(
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissDiscardConfirmation,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreen(
    uiState: EditProfileUiState,
    actions: EditProfileActions,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = (uiState as? EditProfileUiState.Editing)?.saveError?.asText()

    LaunchedEffect(saveErrorMessage) {
        if (saveErrorMessage != null) snackbarHostState.showSnackbar(saveErrorMessage)
    }

    LightStatusBarIcons()

    Scaffold(
        // Painted here rather than handed to the container, so both span the window — see
        // CreatePostScreen. The wash starts at the very top, under the accent bar, so what shows
        // below the bar is the bar's own colour carrying on into the page rather than a band
        // starting on its own; the container is transparent to let it through.
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(rememberAccentWash()),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_edit)) },
                modifier = Modifier.shadow(4.dp),
                colors = accentBarColors(),
                navigationIcon = {
                    AppBarAction(
                        icon = R.drawable.ic_arrow_back,
                        onClick = actions::goBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                },
                actions = {
                    (uiState as? EditProfileUiState.Editing)?.let { editing ->
                        SaveAction(
                            isSaving = editing.isSaving,
                            enabled = editing.isDirty && editing.form.canSave,
                            onSave = actions::save,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when (uiState) {
            EditProfileUiState.Loading -> FullScreenProgress(
                modifier = Modifier.padding(contentPadding),
            )

            is EditProfileUiState.Error -> FullScreenError(
                message = uiState.error.asText(),
                onRetry = actions::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is EditProfileUiState.Editing -> EditProfileContent(
                form = uiState.form,
                isSaving = uiState.isSaving,
                actions = actions,
                onPickAvatar = onPickAvatar,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
            )
        }
    }
}

/** The top-bar save affordance: a text button that yields to a spinner while a save runs. */
@Composable
private fun SaveAction(
    isSaving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    if (isSaving) {
        CircularProgressIndicator(
            strokeWidth = 2.5.dp,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp),
        )
    } else {
        TextButton(onClick = onSave, enabled = enabled) {
            Text(
                stringResource(R.string.edit_profile_save),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun EditProfileContent(
    form: EditProfileForm,
    isSaving: Boolean,
    actions: EditProfileActions,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The avatar stays off the cards on purpose: it is the one thing on the page that is a
        // picture of the user rather than a field about them, and it reads that way floating on
        // the wash instead of boxed in with the text.
        item(key = "avatar") {
            AvatarPicker(
                userId = form.userId,
                name = form.nickname,
                avatarUrl = form.displayAvatar,
                onClick = onPickAvatar,
            )
        }

        item(key = "identity") {
            FormCard {
                OutlinedTextField(
                    value = form.nickname,
                    onValueChange = actions::onNicknameChange,
                    label = { Text(stringResource(R.string.edit_profile_name_label)) },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = form.age,
                    onValueChange = actions::onAgeChange,
                    label = { Text(stringResource(R.string.edit_profile_age_label)) },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    isError = !form.isAgeValid,
                    supportingText = {
                        if (!form.isAgeValid) Text(stringResource(R.string.edit_profile_age_error))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                GenderSelector(
                    selected = form.gender,
                    onSelected = actions::onGenderChange,
                    enabled = !isSaving,
                )
            }
        }

        item(key = "bio") {
            FormCard {
                OutlinedTextField(
                    value = form.bio,
                    onValueChange = actions::onBioChange,
                    label = { Text(stringResource(R.string.edit_profile_bio_label)) },
                    minLines = 4,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The tappable avatar preview with a pencil badge signalling it opens the photo picker. */
@Composable
private fun AvatarPicker(
    userId: UserId,
    name: String,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {
            Avatar(
                userId = userId,
                name = name,
                size = 96.dp,
                imageUrl = avatarUrl,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.edit_profile_change_avatar),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSelector(
    selected: GenderOption?,
    onSelected: (GenderOption) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.edit_profile_gender_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GenderOption.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = GenderOption.entries.size,
                    ),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfileScreenPreview() {
    MosaicTheme {
        EditProfileScreen(
            uiState = EditProfileUiState.Editing(
                form = EditProfileForm.from(SampleUsers.first()),
                isDirty = true,
                isSaving = false,
                showDiscardConfirmation = false,
                saveError = null,
            ),
            actions = createActionsProxy(),
            onPickAvatar = {},
        )
    }
}
