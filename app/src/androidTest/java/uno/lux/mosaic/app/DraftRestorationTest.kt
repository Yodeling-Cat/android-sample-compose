package uno.lux.mosaic.app

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.files.FileLoader
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.data.files.VideoMetadataReader
import uno.lux.mosaic.composer.ui.CreatePostMedia
import uno.lux.mosaic.composer.ui.CreatePostUiEvent
import uno.lux.mosaic.composer.ui.CreatePostViewModel
import uno.lux.mosaic.feed.data.FeedDataSource
import uno.lux.mosaic.feed.data.FeedPage
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.post.data.PostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.user.data.UserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.ui.EditProfileUiEvent
import uno.lux.mosaic.user.ui.EditProfileUiState
import uno.lux.mosaic.user.ui.EditProfileViewModel

/**
 * The other half of surviving process death: [BackStackRestorationTest] pins that the *page* comes
 * back, this pins that what the user had **typed into it** comes back with it.
 *
 * Instrumented because that is where the mechanism actually lives. A draft is written through
 * `androidx.savedstate`'s serializer into a `SavedState` — a `Bundle` on Android — and the round
 * trip below is the real one the platform performs: ask the handle's provider to save, then build
 * a fresh handle from that state, the way the framework does for a restarted process. None of that
 * can be exercised on the JVM, so testing it there would only ever have proven a stand-in.
 *
 * The doubles are inert on purpose: these tests type into a form and restart it, so anything that
 * would reach the network is unreachable and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DraftRestorationTest {

    // viewModelScope runs on Dispatchers.Main; on a device that is the real looper, so the tests
    // would be racing the ViewModel's own init. Same substitution the JVM tests make.
    @Before
    fun setUpMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val ada = User(
        id = "u1",
        nickname = "Ada Lovelace",
        handle = "@countess",
        age = 36,
        bio = "Mathematician & writer.",
    )

    /** The save/restore the framework performs around a process death, as the platform does it. */
    private fun SavedStateHandle.killAndRestore(): SavedStateHandle {
        val saved: SavedState = savedStateProvider().saveState()

        return SavedStateHandle.createHandle(saved, null)
    }

    private fun composer(handle: SavedStateHandle) = CreatePostViewModel(
        feedRepository = FeedRepository(UnusedFeedDataSource, postRepository(), userRepository()),
        fileLoader = PickingFileLoader,
        videoMetadataReader = PickedClipMetadataReader,
        navigator = Navigator().apply { attach(mutableListOf(entryFor(Screen.CreatePost))) },
        savedStateHandle = handle,
    )

    @Test
    fun aPartWrittenPostComesBack() = runTest {
        val handle = SavedStateHandle()
        val killed = composer(handle)
        killed.onEvent(CreatePostUiEvent.TitleChanged("Engine sketches"))
        killed.onEvent(CreatePostUiEvent.BodyChanged("Carry mechanism works."))
        killed.onEvent(CreatePostUiEvent.ImagesPicked(listOf("content://pick/a", "content://pick/b")))

        val restored = composer(handle.killAndRestore())

        val form = restored.uiState.value.form
        assertEquals("Engine sketches", form.title)
        assertEquals("Carry mechanism works.", form.body)
        assertEquals(
            CreatePostMedia.Images(listOf("content://pick/a", "content://pick/b")),
            form.media,
        )
    }

    /**
     * The media hierarchy is what flattening a draft into loose keys would have lost: which
     * variant it is, and the duration read for its badge, both have to come back.
     */
    @Test
    fun anAttachedVideoComesBackWithItsDuration() = runTest {
        val handle = SavedStateHandle()
        composer(handle).onEvent(CreatePostUiEvent.VideoPicked("content://pick/clip"))

        val restored = composer(handle.killAndRestore())

        assertEquals(
            CreatePostMedia.Video(uri = "content://pick/clip", durationSeconds = PICKED_CLIP_SECONDS),
            restored.uiState.value.form.media,
        )
    }

    @Test
    fun anUntouchedComposerKeepsTheEmptyMediaCase() = runTest {
        val handle = SavedStateHandle()
        composer(handle).onEvent(CreatePostUiEvent.TitleChanged("Clip"))

        val restored = composer(handle.killAndRestore())

        // None is a variant like any other, so the closed hierarchy's empty case round-trips too.
        assertEquals(CreatePostMedia.None, restored.uiState.value.form.media)
    }

    // Only the form is worth saving: a publish that was in flight when the process died is not
    // still running, and an error from that process is about a request nobody is waiting on.
    @Test
    fun aRestoredComposerIsIdleAndCarriesNoStaleError() = runTest {
        val handle = SavedStateHandle()
        composer(handle).onEvent(CreatePostUiEvent.TitleChanged("Engine sketches"))

        val restored = composer(handle.killAndRestore())

        val state = restored.uiState.value
        assertEquals("Engine sketches", state.form.title)
        assertFalse(state.isPublishing)
        assertNull(state.error)
        assertFalse(state.showDiscardConfirmation)
    }

    @Test
    fun anUntouchedComposerComesBackBlank() = runTest {
        val handle = SavedStateHandle()
        composer(handle)

        val restored = composer(handle.killAndRestore())

        assertTrue(restored.uiState.value.form.isEmpty)
    }

    private fun editor(handle: SavedStateHandle): EditProfileViewModel {
        val users = UserRepository(StoredUserDataSource(ada)).apply { ingest(listOf(ada)) }

        return EditProfileViewModel(
            userRepository = users,
            fileLoader = PickingFileLoader,
            navigator = Navigator().apply { attach(mutableListOf(entryFor(Screen.EditProfile))) },
            savedStateHandle = handle,
            userId = ada.id,
        )
    }

    private fun TestScope.editing(viewModel: EditProfileViewModel): EditProfileUiState.Editing {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        return viewModel.uiState.value as EditProfileUiState.Editing
    }

    @Test
    fun inProgressEditsComeBack() = runTest {
        val handle = SavedStateHandle()
        val killed = editor(handle)
        editing(killed)
        killed.onEvent(EditProfileUiEvent.NicknameChanged("Ada L."))
        killed.onEvent(EditProfileUiEvent.BioChanged("Rewritten."))

        val restored = editing(editor(handle.killAndRestore()))

        assertEquals("Ada L.", restored.form.nickname)
        assertEquals("Rewritten.", restored.form.bio)
    }

    /**
     * The pristine snapshot is re-read from the server on restore, so edits that were never saved
     * still read as unsaved — which is what keeps the Save button and the discard prompt honest.
     */
    @Test
    fun restoredEditsAreStillDirty() = runTest {
        val handle = SavedStateHandle()
        val killed = editor(handle)
        editing(killed)
        killed.onEvent(EditProfileUiEvent.NicknameChanged("Ada L."))

        val restored = editing(editor(handle.killAndRestore()))

        assertTrue(restored.isDirty)
    }

    @Test
    fun anUntouchedEditorComesBackSeededFromTheUser() = runTest {
        val handle = SavedStateHandle()
        editing(editor(handle))

        val restored = editing(editor(handle.killAndRestore()))

        assertEquals("Ada Lovelace", restored.form.nickname)
        assertFalse(restored.isDirty)
    }

    private fun postRepository() = PostRepository(UnusedPostDataSource, userRepository())

    private fun userRepository() = UserRepository(StoredUserDataSource(ada))

    /** Serves the one user the editor loads, and refuses everything a draft test can't reach. */
    private class StoredUserDataSource(
        private val user: User,
    ) : UserDataSource {
        override suspend fun fetch(userId: UserId): User? = user.takeIf { it.id == userId }

        override suspend fun update(userId: UserId, update: ProfileUpdate): User = unused()

        override suspend fun toggleFollow(user: User): User = unused()
    }

    private object UnusedFeedDataSource : FeedDataSource {
        override suspend fun fetch(cursor: String?): FeedPage = unused()
    }

    private object UnusedPostDataSource : PostDataSource {
        override suspend fun fetch(postId: PostId): PostWithUsers? = unused()

        override suspend fun create(draft: NewPost): PostWithUsers = unused()

        override suspend fun delete(postId: PostId) = unused()

        override suspend fun setLike(postId: PostId, liked: Boolean): LikeState = unused()

        override suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean = unused()

        override suspend fun report(
            postId: PostId,
            reason: ReportReason,
            details: String,
        ) = unused()
    }

    /** Answers the size check a video pick makes; reading bytes only happens on publish. */
    private object PickingFileLoader : FileLoader {
        override suspend fun read(uri: String): FileUpload = unused()

        override suspend fun sizeOf(uri: String): Long = 1_000L
    }

    private object PickedClipMetadataReader : VideoMetadataReader {
        override suspend fun durationSeconds(uri: String): Int = PICKED_CLIP_SECONDS
    }
}

/** What [PickedClipMetadataReader] reports for the picked clip. */
private const val PICKED_CLIP_SECONDS = 42

/** Reached only if a draft test starts doing something a draft test has no business doing. */
private fun unused(): Nothing =
    throw UnsupportedOperationException("Not reachable while only editing a draft")
