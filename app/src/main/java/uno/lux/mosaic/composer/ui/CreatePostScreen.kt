package uno.lux.mosaic.composer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Stable
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
import uno.lux.mosaic.common.util.createActionsProxy
import uno.lux.mosaic.designsystem.components.AppBarAction
import uno.lux.mosaic.designsystem.components.HoldToConfirmButton
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.designsystem.theme.rememberAccentWash
import uno.lux.mosaic.common.R as CommonR

/**
 * The composer's ViewModel-backed intents, as one [Stable] seam the stateless
 * [CreatePostScreen] depends on — the field edits, the media add/remove/preview intents, and
 * publishing and leaving, the navigating ones routed through the injected `Navigator`.
 * [CreatePostViewModel] implements it, so the binder passes the ViewModel directly and a
 * preview passes a no-op [createActionsProxy].
 */
@Stable
interface CreatePostActions {
    fun onTitleChange(value: String)

    fun onBodyChange(value: String)

    fun onImagesPicked(uris: List<String>)

    fun onRemoveImage(uri: String)

    fun onVideoPicked(uri: String)

    fun onRemoveVideo()

    fun openImages(media: CreatePostMedia.Images, initialIndex: Int)

    fun openVideo(media: CreatePostMedia.Video)

    fun publish()

    fun goBack()

    fun dismissDiscardConfirmation()

    fun confirmDiscard()
}

@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickImages = rememberLauncherForActivityResult(
        // The picker caps the selection itself, so the user can't overshoot the album limit;
        // the ViewModel still trims, since a second trip through the picker could push it over.
        ActivityResultContracts.PickMultipleVisualMedia(CREATE_POST_MAX_IMAGES),
    ) { uris ->
        // The picker's session-scoped read grant is enough — the bytes are read and uploaded
        // on publish, so no persistable permission is needed.
        if (uris.isNotEmpty()) viewModel.onImagesPicked(uris.map { it.toString() })
    }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri.toString())
    }

    BackHandler {
        viewModel.goBack()
    }

    CreatePostScreen(
        uiState = uiState,
        actions = viewModel,
        onPickImages = {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onPickVideo = {
            pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        },
        modifier = modifier,
    )

    if (uiState.showDiscardConfirmation) {
        DiscardChangesDialog(
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissDiscardConfirmation,
        )
    }
}

/**
 * Stateless post composer — a title, a body, and either up to [CREATE_POST_MAX_IMAGES] photos or one
 * video. It is pushed over the shell rather than being a tab, so the bar carries an
 * up-affordance. Holding no ViewModel makes it directly previewable and testable; the two pick
 * callbacks are passed in rather than living on [CreatePostActions] because launching the system
 * picker needs a composition-scoped launcher, not a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatePostScreen(
    uiState: CreatePostUiState,
    actions: CreatePostActions,
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
        // The page colour and its wash are painted here rather than handed to the container,
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
                        onClick = actions::goBack,
                        contentDescription = stringResource(CommonR.string.navigate_back),
                    )
                },
            )
        },
    ) { contentPadding ->
        CreatePostForm(
            form = uiState.form,
            isPublishing = uiState.isPublishing,
            actions = actions,
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
    actions: CreatePostActions,
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
                onValueChange = actions::onTitleChange,
                label = { Text(stringResource(R.string.create_post_title_label)) },
                singleLine = true,
                enabled = !isPublishing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text("${form.title.length} / $CREATE_POST_TITLE_MAX_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.body,
                onValueChange = actions::onBodyChange,
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
                actions = actions,
                onPickImages = onPickImages,
                onPickVideo = onPickVideo,
            )
        }

        Spacer(Modifier.weight(1f))

        PublishButton(
            isPublishing = isPublishing,
            enabled = form.canPublish,
            onPublish = actions::publish,
        )
    }
}

/**
 * The draft's media. A post holds photos *or* a video, so the section renders one of three
 * shapes: with nothing chosen both affordances are offered; once photos are picked only the photo
 * strip remains; once a video is picked only it remains. Deliberately **no** affordance for the
 * other kind is shown while one is attached — swapping means removing what's there first, which
 * keeps the exclusivity self-evident rather than something a dialog has to explain.
 */
@Composable
private fun PostMediaPicker(
    media: CreatePostMedia,
    enabled: Boolean,
    actions: CreatePostActions,
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
                onOpenImage = { index -> actions.openImages(media, index) },
                onRemoveImage = actions::onRemoveImage,
            )

            is CreatePostMedia.Video -> PickedMediaThumbnail(
                uri = media.uri,
                removeDescription = stringResource(R.string.create_post_remove_video),
                enabled = enabled,
                onOpen = { actions.openVideo(media) },
                onRemove = actions::onRemoveVideo,
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

/** Nothing attached yet: both kinds on offer, side by side. */
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

/** The picked photos as a scrolling strip, with the add tile trailing until the limit is hit. */
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

/**
 * One picked file: a preview of it with a remove affordance. Coil renders both kinds from the
 * content URI — a still directly, a video via the frame decoder `MosaicApp` registers. Tapping
 * the thumbnail opens the file full screen through [onOpen] — photos in the album viewer, the
 * clip on the video page. [overlay] carries whatever else the kind needs on top, which today is
 * the video's duration badge.
 */
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

/**
 * A tile that opens a picker — one per media kind still on offer. It wears the soft accent rather
 * than an outline, because on a page whose every other control is a field, these two are the only
 * invitations, and a grey box on a white card does not read as one.
 */
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

/**
 * The publish affordance. Publishing is the one irreversible step in the composer — it puts the
 * post in front of everyone — so it asks to be *held* rather than tapped, and its label yields to
 * a spinner while the upload runs.
 */
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

/** Stable list key for the trailing add tile, so it isn't confused with an image URI. */
private const val ADD_PHOTOS_TILE_KEY = "add-photos"

@Preview(name = "Prefilled form", showBackground = true)
@Composable
private fun CreatePostScreenPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = CreatePostUiState(
                form = CreatePostForm(
                    title = "Difference Engine, sketch 12",
                    body = "Finally got the carry mechanism to behave.",
                ),
            ),
            actions = createActionsProxy(),
            onPickImages = {},
            onPickVideo = {},
        )
    }
}

/** The page as it opens: nothing typed, both media tiles on offer, publishing not yet possible. */
@Preview(name = "Empty form", showBackground = true)
@Composable
private fun CreatePostScreenEmptyPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = CreatePostUiState(),
            actions = createActionsProxy(),
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
            uiState = CreatePostUiState(
                form = CreatePostForm(
                    title = "The engine, running",
                    body = "Forty seconds of the carry mechanism in motion.",
                    media = CreatePostMedia.Video(uri = "", durationSeconds = 42),
                ),
            ),
            actions = createActionsProxy(),
            onPickImages = {},
            onPickVideo = {},
        )
    }
}

/** The in-flight publish, where every affordance is inert and the button yields to its spinner. */
@Preview(name = "Publishing", showBackground = true)
@Composable
private fun CreatePostScreenPublishingPreview() {
    MosaicTheme {
        CreatePostScreen(
            uiState = CreatePostUiState(
                form = CreatePostForm(
                    title = "The engine, running",
                    body = "Forty seconds of the carry mechanism in motion.",
                    media = CreatePostMedia.Video(uri = "", durationSeconds = 42),
                ),
                isPublishing = true,
            ),
            actions = createActionsProxy(),
            onPickImages = {},
            onPickVideo = {},
        )
    }
}
