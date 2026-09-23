package uno.lux.mosaic.composer.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.files.FakeFileLoader
import uno.lux.mosaic.common.data.files.FakeVideoMetadataReader
import uno.lux.mosaic.common.data.network.httpException
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.feed.data.FakeFeedDataSource
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.post.data.FakePostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.NewPostMedia
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import java.io.IOException

class CreatePostViewModelTest : ViewModelTest() {

    // The composer is pushed over the shell, so it always has an entry of its own to leave.
    private val backStack = backStackOf(Screen.Shell, Screen.CreatePost)
    private val navigator = Navigator().apply { attach(backStack) }

    private data class Fixture(
        val viewModel: CreatePostViewModel,
        val postDataSource: FakePostDataSource,
    )

    private fun fixture(
        createError: Exception? = null,
        fileError: Exception? = null,
        videoSize: Long? = 1_000,
        videoDuration: Int = 12,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): Fixture {
        val postDataSource = FakePostDataSource().apply { this.createError = createError }
        val userRepository = UserRepository(FakeUserDataSource())
        val feedRepository = FeedRepository(
            dataSource = FakeFeedDataSource(),
            postRepository = PostRepository(postDataSource, userRepository),
            userRepository = userRepository,
        )

        return Fixture(
            CreatePostViewModel(
                feedRepository,
                FakeFileLoader(error = fileError, size = videoSize),
                FakeVideoMetadataReader(videoDuration),
                navigator,
                savedStateHandle,
            ),
            postDataSource,
        )
    }

    /** The picked image URIs, or an empty list when the draft holds no photos. */
    private val CreatePostForm.imageUris: List<String>
        get() = (media as? CreatePostMedia.Images)?.uris.orEmpty()

    private fun CreatePostViewModel.fillIn(title: String = "Title", body: String = "Body") {
        onEvent(CreatePostUiEvent.TitleChanged(title))
        onEvent(CreatePostUiEvent.BodyChanged(body))
    }

    @Test
    fun `the form starts blank and cannot be published`() = runTest {
        val state = fixture().viewModel.uiState.first()

        assertTrue(state.form.isEmpty)
        assertFalse(state.form.canPublish)
        assertFalse(state.isPublishing)
    }

    @Test
    fun `editing the fields updates the form`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.fillIn(title = "Engine sketches", body = "Carry mechanism works.")

        val form = viewModel.uiState.first().form
        assertEquals("Engine sketches", form.title)
        assertEquals("Carry mechanism works.", form.body)
        assertTrue(form.canPublish)
    }

    @Test
    fun `a blank field keeps publishing disabled`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.fillIn(title = "   ", body = "Body")

        assertFalse(
            viewModel.uiState
                .first()
                .form.canPublish,
        )
    }

    @Test
    fun `the fields are capped at the backend's limits`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.fillIn(title = "t".repeat(CREATE_POST_TITLE_MAX_LENGTH + 10), body = "b")

        assertEquals(
            CREATE_POST_TITLE_MAX_LENGTH,
            viewModel.uiState
                .first()
                .form.title.length,
        )
    }

    @Test
    fun `Publish sends the trimmed draft`() = runTest {
        val (viewModel, dataSource) = fixture()
        viewModel.fillIn(title = "  Title  ", body = "  Body  ")

        viewModel.onEvent(CreatePostUiEvent.Publish)

        assertEquals("Title", dataSource.lastDraft?.title)
        assertEquals("Body", dataSource.lastDraft?.body)
    }

    @Test
    fun `a published post clears the form and replaces the composer with its detail page`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.fillIn()

        viewModel.onEvent(CreatePostUiEvent.Publish)

        assertTrue(
            viewModel.uiState
                .first()
                .form.isEmpty,
        )
        assertFalse(viewModel.uiState.first().isPublishing)
        // Replaced, not stacked: backing out of the new post must not land on the composer.
        assertEquals(listOf(Screen.Shell, Screen.PostDetail("p-new")), backStack.screens())
    }

    @Test
    fun `Publish is a no-op while the form is incomplete`() = runTest {
        val (viewModel, dataSource) = fixture()
        viewModel.onEvent(CreatePostUiEvent.TitleChanged("Title"))

        viewModel.onEvent(CreatePostUiEvent.Publish)

        assertNull(dataSource.lastDraft)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `a failed publish keeps the typed text and surfaces the error`() = runTest {
        val viewModel = fixture(createError = IOException("offline")).viewModel
        viewModel.fillIn(title = "Title", body = "Body")

        viewModel.onEvent(CreatePostUiEvent.Publish)

        val state = viewModel.uiState.first()
        assertEquals("Title", state.form.title)
        assertEquals(CreatePostError.Failed(AppError.Unknown), state.error)
        assertFalse(state.isPublishing)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    /**
     * The composer mirrors the server's validations, but the day the mirrors drift the server's
     * structured 422 is what arrives — and its message, not a generic apology, is what must show.
     */
    @Test
    fun `a publish the server refuses surfaces the server's own message`() = runTest {
        val viewModel = fixture(
            createError = httpException(
                422,
                """
                {"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[
                  {"path":"title","code":"too_long","message":"Title is too long (maximum is 120 characters)"}
                ]}}
                """.trimIndent(),
            ),
        ).viewModel
        viewModel.fillIn(title = "Title", body = "Body")

        viewModel.onEvent(CreatePostUiEvent.Publish)

        val state = viewModel.uiState.first()
        assertEquals("Title", state.form.title)
        assertEquals(
            CreatePostError.Failed(
                AppError.Http(code = 422, serverMessage = "Title is too long (maximum is 120 characters)"),
            ),
            state.error,
        )
    }

    @Test
    fun `picked images are added to the form in order`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a", "uri-b")))
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-c")))

        assertEquals(
            listOf("uri-a", "uri-b", "uri-c"),
            viewModel.uiState
                .first()
                .form.imageUris,
        )
    }

    @Test
    fun `re-picking an already chosen image does not duplicate it`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a", "uri-b")))
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a", "uri-c")))

        assertEquals(
            listOf("uri-a", "uri-b", "uri-c"),
            viewModel.uiState
                .first()
                .form.imageUris,
        )
    }

    @Test
    fun `the selection is capped at the album limit`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(List(CREATE_POST_MAX_IMAGES + 5) { "uri-$it" }))

        val media = viewModel.uiState
            .first()
            .form.media as CreatePostMedia.Images
        assertEquals(CREATE_POST_MAX_IMAGES, media.uris.size)
        assertFalse(media.canAddMore)
    }

    @Test
    fun `a removed image leaves the rest in place`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a", "uri-b", "uri-c")))

        viewModel.onEvent(CreatePostUiEvent.RemoveImage("uri-b"))

        assertEquals(
            listOf("uri-a", "uri-c"),
            viewModel.uiState
                .first()
                .form.imageUris,
        )
    }

    @Test
    fun `removing the last image returns to no media, re-offering video`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a")))

        viewModel.onEvent(CreatePostUiEvent.RemoveImage("uri-a"))

        assertEquals(
            CreatePostMedia.None,
            viewModel.uiState
                .first()
                .form.media,
        )
    }

    @Test
    fun `Publish uploads the picked images with the draft`() = runTest {
        val (viewModel, dataSource) = fixture()
        viewModel.fillIn()
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a", "uri-b")))

        viewModel.onEvent(CreatePostUiEvent.Publish)

        val media = dataSource.lastDraft?.media as NewPostMedia.Images
        assertEquals(listOf("uri-a", "uri-b"), media.files.map { it.filename })
    }

    /** The duration badges the composer's own thumbnail; it is not what gets uploaded. */
    @Test
    fun `a picked video is attached with the duration read from the file`() = runTest {
        val viewModel = fixture(videoDuration = 42).viewModel

        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        assertEquals(
            CreatePostMedia.Video(uri = "clip", durationSeconds = 42),
            viewModel.uiState
                .first()
                .form.media,
        )
    }

    @Test
    fun `an oversized video is refused without being attached`() = runTest {
        val viewModel = fixture(videoSize = CREATE_POST_MAX_VIDEO_BYTES + 1).viewModel

        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        val state = viewModel.uiState.first()
        assertEquals(CreatePostMedia.None, state.form.media)
        assertEquals(CreatePostError.VideoTooLarge, state.error)
    }

    @Test
    fun `a video of unknown size is left to the server rather than refused`() = runTest {
        val viewModel = fixture(videoSize = null).viewModel

        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        val state = viewModel.uiState.first()
        assertTrue(state.form.media is CreatePostMedia.Video)
        assertNull(state.error)
    }

    @Test
    fun `picking images is a no-op while a video is attached`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a")))

        assertTrue(
            viewModel.uiState
                .first()
                .form.media is CreatePostMedia.Video,
        )
    }

    @Test
    fun `a removed video returns to no media`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        viewModel.onEvent(CreatePostUiEvent.RemoveVideo)

        assertEquals(
            CreatePostMedia.None,
            viewModel.uiState
                .first()
                .form.media,
        )
    }

    @Test
    fun `OpenImages pushes the album viewer at the tapped photo`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.OpenImages(CreatePostMedia.Images(listOf("uri-a", "uri-b")), initialIndex = 1))

        assertEquals(Screen.AlbumViewer(listOf("uri-a", "uri-b"), initialIndex = 1), backStack.last().screen)
    }

    @Test
    fun `OpenVideo pushes the fullscreen player for the picked clip`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.OpenVideo(CreatePostMedia.Video(uri = "clip", durationSeconds = 12)))

        // The key carries no title — a clip picked from disk has none.
        assertEquals(Screen.FullscreenVideo(url = "clip"), backStack.last().screen)
    }

    /**
     * The draft carries the file and nothing else — the duration the composer read for its own
     * badge stays on the UI side, since the server derives the stored video's own.
     */
    @Test
    fun `Publish uploads the video with the draft`() = runTest {
        val (viewModel, dataSource) = fixture(videoDuration = 7)
        viewModel.fillIn()
        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        viewModel.onEvent(CreatePostUiEvent.Publish)

        val media = dataSource.lastDraft?.media as NewPostMedia.Video
        assertEquals("clip", media.file.filename)
    }

    @Test
    fun `a text-only post carries no media`() = runTest {
        val (viewModel, dataSource) = fixture()
        viewModel.fillIn()

        viewModel.onEvent(CreatePostUiEvent.Publish)

        assertEquals(NewPostMedia.None, dataSource.lastDraft?.media)
    }

    @Test
    fun `an unreadable image fails the publish and keeps the form`() = runTest {
        val (viewModel, dataSource) = fixture(fileError = IOException("gone"))
        viewModel.fillIn(title = "Title", body = "Body")
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a")))

        viewModel.onEvent(CreatePostUiEvent.Publish)

        assertNull(dataSource.lastDraft)
        val state = viewModel.uiState.first()
        assertEquals("Title", state.form.title)
        assertEquals(listOf("uri-a"), state.form.imageUris)
        assertEquals(CreatePostError.Failed(AppError.Unknown), state.error)
        assertFalse(state.isPublishing)
    }

    @Test
    fun `images alone count as a part-written post`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.ImagesPicked(listOf("uri-a")))

        viewModel.onEvent(CreatePostUiEvent.GoBack)

        assertTrue(viewModel.uiState.first().showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `a video alone counts as a part-written post`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.VideoPicked("clip"))

        viewModel.onEvent(CreatePostUiEvent.GoBack)

        assertTrue(viewModel.uiState.first().showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `GoBack leaves straight away when nothing has been typed`() = runTest {
        val viewModel = fixture().viewModel

        viewModel.onEvent(CreatePostUiEvent.GoBack)

        assertFalse(viewModel.uiState.first().showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `GoBack asks before dropping a part-written post`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.onEvent(CreatePostUiEvent.TitleChanged("Half a thought"))

        viewModel.onEvent(CreatePostUiEvent.GoBack)

        assertTrue(viewModel.uiState.first().showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `confirming the discard leaves the composer`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.fillIn()
        viewModel.onEvent(CreatePostUiEvent.GoBack)

        viewModel.onEvent(CreatePostUiEvent.ConfirmDiscard)

        assertFalse(viewModel.uiState.first().showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `dismissing the discard stays put and keeps the typed text`() = runTest {
        val viewModel = fixture().viewModel
        viewModel.fillIn(title = "Half a thought")
        viewModel.onEvent(CreatePostUiEvent.GoBack)

        viewModel.onEvent(CreatePostUiEvent.DismissDiscard)

        val state = viewModel.uiState.first()
        assertFalse(state.showDiscardConfirmation)
        assertEquals("Half a thought", state.form.title)
        assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }
}
