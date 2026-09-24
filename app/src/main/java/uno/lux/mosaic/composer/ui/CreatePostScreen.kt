package uno.lux.mosaic.composer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import uno.lux.mosaic.R
import uno.lux.mosaic.common.formatVideoDuration
import uno.lux.mosaic.common.ui.DiscardChangesDialog
import uno.lux.mosaic.common.ui.FormCard
import uno.lux.mosaic.common.ui.MediaBadge
import uno.lux.mosaic.common.ui.MediaRemoveButton
import uno.lux.mosaic.designsystem.components.AppBarAction
import uno.lux.mosaic.designsystem.components.HoldToConfirmButton
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.designsystem.theme.rememberAccentWash
import uno.lux.mosaic.common.R as CommonR
import uno.lux.mosaic.composer.ui.CreatePostUiEvent as UiEvent
import uno.lux.mosaic.composer.ui.CreatePostUiState as UiState

@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickImages = rememberLauncherForActivityResult(
        // The picker caps the selection itself, so the user can't overshoot the album limit;
        // the ViewModel still trims, since a second trip through the picker could push it over.
        PickMultipleVisualMedia(CREATE_POST_MAX_IMAGES),
    ) { uris ->
        // The picker's session-scoped read grant is enough — the bytes are read and uploaded
        // on publish, so no persistable permission is needed.
        if (uris.isNotEmpty()) {
            viewModel.onEvent(UiEvent.ImagesPicked(uris.map { it.toString() }))
        }
    }

    val pickVideo = rememberLauncherForActivityResult(
        PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onEvent(UiEvent.VideoPicked(uri.toString()))
    }

    BackHandler {
        viewModel.onEvent(UiEvent.GoBack)
    }

    CreatePostScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onPickImages = {
            pickImages.launch(
                PickVisualMediaRequest(PickVisualMedia.ImageOnly),
            )
        },
        onPickVideo = {
            pickVideo.launch(
                PickVisualMediaRequest(PickVisualMedia.VideoOnly),
            )
        },
        modifier = modifier,
    )

    if (uiState.showDiscardConfirmation) {
        DiscardChangesDialog(
            onConfirm = { viewModel.onEvent(UiEvent.ConfirmDiscard) },
            onDismiss = { viewModel.onEvent(UiEvent.DismissDiscard) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatePostScreen(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.asText()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbarHostState.showSnackbar(errorMessage)
    }

    Scaffold(
        // The page color and its wash are painted here rather than handed to the container,
        // because they have to span the window: put on the form they would stop at the content
        // padding and leave the inset strips showing through. The container is then transparent,
        // since a Scaffold paints its own over anything the modifier drew.
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .background(rememberAccentWash()),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        // Includes the IME, so the keyboard arrives as content padding — consumed below, unlike
        // an `imePadding()` that would re-apply the navigation bar inset already spent there.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_post_title)) },
                // Transparent, and unshadowed with it: the form is inset below the bar and never
                // scrolls under it, so the bar has no edge to cast one over — and an opaque bar
                // would cover the very top of the wash, which is the strongest part of it.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    AppBarAction(
                        icon = CommonR.drawable.ic_arrow_back,
                        onClick = { onEvent(UiEvent.GoBack) },
                        contentDescription = stringResource(CommonR.string.navigate_back),
                    )
                },
            )
        },
    ) { contentPadding ->
        CreatePostForm(
            form = uiState.form,
            isPublishing = uiState.isPublishing,
            onEvent = onEvent,
            onPickImages = onPickImages,
            onPickVideo = onPickVideo,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding),
        )
    }
}

@Composable
private fun CreatePostForm(
    form: CreatePostForm,
    isPublishing: Boolean,
    onEvent: (UiEvent) -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FormCard {
            OutlinedTextField(
                value = form.title,
                onValueChange = { onEvent(UiEvent.TitleChanged(it)) },
                label = { Text(stringResource(R.string.create_post_title_label)) },
                singleLine = true,
                enabled = !isPublishing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text("${form.title.length} / $CREATE_POST_TITLE_MAX_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.body,
                onValueChange = { onEvent(UiEvent.BodyChanged(it)) },
                label = { Text(stringResource(R.string.create_post_body_label)) },
                minLines = 8,
                enabled = !isPublishing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormCard {
            PostMediaPicker(
                media = form.media,
                enabled = !isPublishing,
                onEvent = onEvent,
                onPickImages = onPickImages,
                onPickVideo = onPickVideo,
            )
        }

        Spacer(Modifier.weight(1f))

        PublishButton(
            isPublishing = isPublishing,
            enabled = form.canPublish,
            onPublish = { onEvent(UiEvent.Publish) },
        )
    }
}

@Composable
private fun PostMediaPicker(
    media: CreatePostMedia,
    enabled: Boolean,
    onEvent: (UiEvent) -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                when (media) {
                    is CreatePostMedia.Images -> R.string.create_post_photos_label
                    else -> R.string.create_post_media_label
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (media) {
            CreatePostMedia.None -> EmptyMediaTiles(
                enabled = enabled,
                onPickImages = onPickImages,
                onPickVideo = onPickVideo,
            )

            is CreatePostMedia.Images -> PickedImages(
                media = media,
                enabled = enabled,
                onPickImages = onPickImages,
                onOpenImage = { index -> onEvent(UiEvent.OpenImages(media, index)) },
                onRemoveImage = { uri -> onEvent(UiEvent.RemoveImage(uri)) },
            )

            is CreatePostMedia.Video -> PickedMediaThumbnail(
                uri = media.uri,
                removeDescription = stringResource(R.string.create_post_remove_video),
                enabled = enabled,
                onOpen = { onEvent(UiEvent.OpenVideo(media)) },
                onRemove = { onEvent(UiEvent.RemoveVideo) },
            ) {
                MediaBadge(
                    text = formatVideoDuration(media.durationSeconds),
                    iconRes = CommonR.drawable.ic_play_arrow,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyMediaTiles(
    enabled: Boolean,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaTile(
            iconRes = R.drawable.ic_image,
            labelRes = R.string.create_post_add_photos,
            enabled = enabled,
            onClick = onPickImages,
        )

        MediaTile(
            iconRes = CommonR.drawable.ic_play_arrow,
            labelRes = R.string.create_post_add_video,
            enabled = enabled,
            onClick = onPickVideo,
        )
    }
}

@Composable
private fun PickedImages(
    media: CreatePostMedia.Images,
    enabled: Boolean,
    onPickImages: () -> Unit,
    onOpenImage: (index: Int) -> Unit,
    onRemoveImage: (uri: String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Keyed by URI so removing one thumbnail doesn't recompose (or re-fetch) the rest.
        itemsIndexed(media.uris, key = { _, uri -> uri }) { index, uri ->
            PickedMediaThumbnail(
                uri = uri,
                removeDescription = stringResource(R.string.create_post_remove_photo),
                enabled = enabled,
                onOpen = { onOpenImage(index) },
                onRemove = { onRemoveImage(uri) },
                modifier = Modifier.animateItem(),
            )
        }

        if (media.canAddMore) {
            item(key = ADD_PHOTOS_TILE_KEY) {
                MediaTile(
                    iconRes = R.drawable.ic_add,
                    labelRes = R.string.create_post_add_photos,
                    enabled = enabled,
                    onClick = onPickImages,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    Text(
        text = "${media.uris.size} / $CREATE_POST_MAX_IMAGES",
        style = MaterialTheme.typography.bodySmall,
        color = LocalMosaicColors.current.textTertiary,
    )
}

@Composable
private fun PickedMediaThumbnail(
    uri: String,
    removeDescription: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.size(ThumbnailSize)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .debouncedClickable(enabled = enabled, onClick = onOpen),
        )

        overlay()

        MediaRemoveButton(
            contentDescription = removeDescription,
            enabled = enabled,
            onRemove = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun MediaTile(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .size(ThumbnailSize)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .debouncedClickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
        )

        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun PublishButton(
    isPublishing: Boolean,
    enabled: Boolean,
    onPublish: () -> Unit,
) {
    HoldToConfirmButton(
        text = stringResource(R.string.create_post_publish),
        onConfirm = onPublish,
        enabled = enabled,
        isBusy = isPublishing,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val ThumbnailSize = 88.dp

private const val ADD_PHOTOS_TILE_KEY = "add-photos"

@Preview(name = "Prefilled form", showBackground = true)
@Composable
private fun CreatePostScreenPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = UiState(
                form = CreatePostForm(
                    title = "Difference Engine, sketch 12",
                    body = "Finally got the carry mechanism to behave.",
                ),
            ),
            onEvent = {},
            onPickImages = {},
            onPickVideo = {},
        )
    }
}

@Preview(name = "Empty form", showBackground = true)
@Composable
private fun CreatePostScreenEmptyPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = UiState(),
            onEvent = {},
            onPickImages = {},
            onPickVideo = {},
        )
    }
}

@Preview(name = "With a video", showBackground = true)
@Composable
private fun CreatePostScreenVideoPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = UiState(
                form = CreatePostForm(
                    title = "The engine, running",
                    body = "Forty seconds of the carry mechanism in motion.",
                    media = CreatePostMedia.Video(uri = "", durationSeconds = 42),
                ),
            ),
            onEvent = {},
            onPickImages = {},
            onPickVideo = {},
        )
    }
}

@Preview(name = "Publishing", showBackground = true)
@Composable
private fun CreatePostScreenPublishingPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = UiState(
                form = CreatePostForm(
                    title = "The engine, running",
                    body = "Forty seconds of the carry mechanism in motion.",
                    media = CreatePostMedia.Video(uri = "", durationSeconds = 42),
                ),
                isPublishing = true,
            ),
            onEvent = {},
            onPickImages = {},
            onPickVideo = {},
        )
    }
}
