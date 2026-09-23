package uno.lux.mosaic.composer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.files.FileLoader
import uno.lux.mosaic.common.data.files.VideoMetadataReader
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.common.util.restoreDraft
import uno.lux.mosaic.common.util.saveDraft
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.post.data.domain.NewPostMedia
import javax.inject.Inject
import uno.lux.mosaic.composer.ui.CreatePostUiEvent as UiEvent
import uno.lux.mosaic.composer.ui.CreatePostUiState as UiState

/**
 * Drives the post composer. Publishing goes through [FeedRepository].
 *
 * A failed publishing keeps the typed text and surfaces the error, so nothing is lost to a dropped connection.
 *
 * Leaving with a part-written post asks first, raises the confirmation instead of popping when the form has content.
 */
@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val fileLoader: FileLoader,
    private val videoMetadataReader: VideoMetadataReader,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(form = savedStateHandle.restoreDraft(DRAFT_KEY) ?: CreatePostForm()),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var publishJob: Job? = null
    private var pickVideoJob: Job? = null

    init {
        savedStateHandle.saveDraft(DRAFT_KEY) { _uiState.value.form }
    }

    fun onEvent(event: UiEvent): Unit = when (event) {
        is UiEvent.TitleChanged -> {
            updateForm { it.copy(title = event.value.take(CREATE_POST_TITLE_MAX_LENGTH)) }
        }

        is UiEvent.BodyChanged -> {
            updateForm { it.copy(body = event.value.take(CREATE_POST_BODY_MAX_LENGTH)) }
        }

        is UiEvent.ImagesPicked -> {
            addImages(event.uris)
        }

        is UiEvent.RemoveImage -> {
            removeImage(event.uri)
        }

        is UiEvent.VideoPicked -> {
            attachVideo(event.uri)
        }

        UiEvent.RemoveVideo -> {
            removeVideo()
        }

        is UiEvent.OpenImages -> {
            navigator.goTo(Screen.AlbumViewer(event.media.uris, event.initialIndex))
        }

        is UiEvent.OpenVideo -> {
            navigator.goTo(Screen.FullscreenVideo(event.media.uri))
        }

        UiEvent.Publish -> {
            publish()
        }

        UiEvent.GoBack -> {
            goBack()
        }

        UiEvent.DismissDiscard -> {
            _uiState.update { it.copy(showDiscardConfirmation = false) }
        }

        UiEvent.ConfirmDiscard -> {
            _uiState.update { it.copy(showDiscardConfirmation = false) }
            navigator.goBack()
        }
    }

    /**
     * Adds a picked selection. Already-picked URIs are dropped rather than duplicated.
     *
     * A no-op while a video is attached: the screen hides the photo affordance in that state, so
     * reaching here would mean the two media kinds were about to coexist.
     */
    private fun addImages(uris: List<String>) = updateForm { form ->
        val current = when (val media = form.media) {
            CreatePostMedia.None -> emptyList()
            is CreatePostMedia.Images -> media.uris
            is CreatePostMedia.Video -> return@updateForm form
        }

        val added = uris.filterNot { it in current }
        form.copy(media = CreatePostMedia.Images((current + added).take(CREATE_POST_MAX_IMAGES)))
    }

    /** Removing the last photo returns to [CreatePostMedia.None], re-offering the video option. */
    private fun removeImage(uri: String) = updateForm { form ->
        val media = form.media as? CreatePostMedia.Images ?: return@updateForm form
        val remaining = media.uris - uri

        form.copy(media = if (remaining.isEmpty()) CreatePostMedia.None else media.copy(remaining))
    }

    /**
     * Attaches a picked clip, rejecting one over [CREATE_POST_MAX_VIDEO_BYTES] before its bytes are
     * ever read. A provider that reports no size is allowed through and left to the server, since
     * refusing an unknown-size file would block legitimate ones.
     *
     * Duration is read here, at pick time, because it is cheap (metadata only) and the thumbnail
     * badges it — the upload itself carries no duration, the server deriving that from the file.
     */
    private fun attachVideo(uri: String) {
        launchIfIdle(::pickVideoJob) {
            catchErrors(
                onError = { e ->
                    _uiState.update { it.copy(error = CreatePostError.Failed(e.toAppError())) }
                },
            ) {
                val size = fileLoader.sizeOf(uri)

                if (size != null && size > CREATE_POST_MAX_VIDEO_BYTES) {
                    _uiState.update { it.copy(error = CreatePostError.VideoTooLarge) }
                    return@catchErrors
                }

                val duration = videoMetadataReader.durationSeconds(uri)
                _uiState.update { state ->
                    state.copy(
                        form = state.form.copy(
                            media = CreatePostMedia.Video(uri = uri, durationSeconds = duration),
                        ),
                        error = null,
                    )
                }
            }
        }
    }

    private fun removeVideo() = updateForm { form ->
        if (form.media is CreatePostMedia.Video) form.copy(media = CreatePostMedia.None) else form
    }

    private fun publish() {
        if (!_uiState.value.form.canPublish) return

        launchIfIdle(::publishJob) {
            val form = _uiState.value.form
            _uiState.update { it.copy(isPublishing = true, error = null) }

            catchErrors(
                onError = { e ->
                    _uiState.update {
                        it.copy(isPublishing = false, error = CreatePostError.Failed(e.toAppError()))
                    }
                },
            ) {
                val draft = form.toNewPost(media = loadMedia(form.media))

                val postId = feedRepository.publish(draft)
                _uiState.value = UiState()
                navigator.replaceTop(Screen.PostDetail(postId))
            }
        }
    }

    private fun goBack() {
        if (_uiState.value.form.isEmpty) {
            navigator.goBack()
        } else {
            _uiState.update { it.copy(showDiscardConfirmation = true) }
        }
    }

    private suspend fun loadMedia(media: CreatePostMedia): NewPostMedia = when (media) {
        CreatePostMedia.None -> {
            NewPostMedia.None
        }

        is CreatePostMedia.Images -> {
            NewPostMedia.Images(media.uris.map { fileLoader.read(it) })
        }

        is CreatePostMedia.Video -> {
            NewPostMedia.Video(fileLoader.read(media.uri))
        }
    }

    private fun updateForm(transform: (CreatePostForm) -> CreatePostForm) {
        _uiState.update { it.copy(form = transform(it.form)) }
    }
}

/** Where the in-progress draft is kept in the entry's saved state. */
private const val DRAFT_KEY = "create_post_draft"
