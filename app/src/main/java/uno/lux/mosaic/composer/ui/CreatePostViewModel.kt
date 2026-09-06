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
import uno.lux.mosaic.app.util.catchErrors
import uno.lux.mosaic.app.util.launchIfIdle
import uno.lux.mosaic.app.util.restoreDraft
import uno.lux.mosaic.app.util.saveDraft
import uno.lux.mosaic.common.data.files.FileLoader
import uno.lux.mosaic.common.data.files.VideoMetadataReader
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.post.data.domain.NewPostMedia
import javax.inject.Inject

/**
 * Drives the post composer. Publishing goes through [FeedRepository], which stores the new post
 * and puts it at the head of the feed; the composer then [replaces][Navigator.replaceTop] itself
 * with the new post's detail page — confirmation the post exists, and backing out of it returns
 * to the shell rather than to a composer the user is done with. A failed publish keeps the typed
 * text and surfaces the error, so nothing is lost to a dropped connection.
 *
 * Leaving with a part-written post asks first, mirroring the profile editor: [goBack] raises the
 * confirmation instead of popping when the form has content.
 */
@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val fileLoader: FileLoader,
    private val videoMetadataReader: VideoMetadataReader,
    private val navigator: Navigator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(),
    CreatePostActions {

    // Only the form is saved. Whether a publish is in flight, and anything that failed, belong to
    // the process that was killed — the restored composer is idle, holding what the user typed.
    private val _uiState = MutableStateFlow(
        CreatePostUiState(form = savedStateHandle.restoreDraft(DRAFT_KEY) ?: CreatePostForm()),
    )
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private var publishJob: Job? = null
    private var pickVideoJob: Job? = null

    init {
        savedStateHandle.saveDraft(DRAFT_KEY) { _uiState.value.form }
    }

    override fun onTitleChange(value: String) = updateForm {
        it.copy(title = value.take(CREATE_POST_TITLE_MAX_LENGTH))
    }

    override fun onBodyChange(value: String) = updateForm {
        it.copy(body = value.take(CREATE_POST_BODY_MAX_LENGTH))
    }

    /**
     * Adds a picked selection, keeping it within [CREATE_POST_MAX_IMAGES]. Already-picked URIs are
     * dropped rather than duplicated — the system picker re-reports every image chosen in a
     * session, so a second trip through it would otherwise repeat the earlier ones.
     *
     * A no-op while a video is attached: the screen hides the photo affordance in that state, so
     * reaching here would mean the two media kinds were about to coexist.
     */
    override fun onImagesPicked(uris: List<String>) = updateForm { form ->
        val current = when (val media = form.media) {
            is CreatePostMedia.Images -> media.uris
            CreatePostMedia.None -> emptyList()
            is CreatePostMedia.Video -> return@updateForm form
        }

        val added = uris.filterNot { it in current }
        form.copy(media = CreatePostMedia.Images((current + added).take(CREATE_POST_MAX_IMAGES)))
    }

    /** Removing the last photo returns to [CreatePostMedia.None], re-offering the video option. */
    override fun onRemoveImage(uri: String) = updateForm { form ->
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
    override fun onVideoPicked(uri: String) {
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

    override fun onRemoveVideo() = updateForm { form ->
        if (form.media is CreatePostMedia.Video) form.copy(media = CreatePostMedia.None) else form
    }

    /** Opens the picked photos in the album viewer, starting at the tapped one. */
    override fun openImages(media: CreatePostMedia.Images, initialIndex: Int) =
        navigator.goTo(Screen.AlbumViewer(media.uris, initialIndex))

    /** Plays the picked clip on the full-screen video page, straight from its content URI. */
    override fun openVideo(media: CreatePostMedia.Video) =
        navigator.goTo(Screen.FullscreenVideo(media.uri))

    override fun publish() {
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
                // The picked files are only read into memory here, at the point of upload —
                // an unreadable URI fails the publishing like any other error, keeping the form.
                val draft = form.toNewPost(media = loadMedia(form.media))

                // A published draft leaves nothing to edit, so the whole state resets — which
                // clears the in-flight flag along with the form.
                val postId = feedRepository.publish(draft)
                _uiState.value = CreatePostUiState()
                navigator.replaceTop(Screen.PostDetail(postId))
            }
        }
    }

    override fun goBack() {
        if (_uiState.value.form.isEmpty) {
            navigator.goBack()
        } else {
            _uiState.update { it.copy(showDiscardConfirmation = true) }
        }
    }

    override fun dismissDiscardConfirmation() {
        _uiState.update { it.copy(showDiscardConfirmation = false) }
    }

    override fun confirmDiscard() {
        _uiState.update { it.copy(showDiscardConfirmation = false) }
        navigator.goBack()
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
