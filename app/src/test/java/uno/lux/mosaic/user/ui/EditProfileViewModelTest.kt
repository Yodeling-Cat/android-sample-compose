package uno.lux.mosaic.user.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.common.data.files.FakeFileLoader
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.testing.ViewModelTest
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens
import uno.lux.mosaic.user.data.FakeUserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest : ViewModelTest() {

    private val ada = User(
        id = "u1",
        nickname = "Ada Lovelace",
        handle = "@countess",
        age = 36,
        gender = "Woman",
        bio = "Mathematician & writer.",
    )

    /** What the shared fake hands back for a picked avatar, so assertions can name the bytes. */
    private val avatarUpload = FileUpload(byteArrayOf(1, 2, 3), "image/png", "avatar.png")

    private data class Fixture(
        val viewModel: EditProfileViewModel,
        val repository: UserRepository,
        val dataSource: FakeUserDataSource,
        val avatarLoader: FakeFileLoader,
    )

    // The editor sits on top of the profile that opened it; a successful save must pop it.
    private val backStack = backStackOf(Screen.Shell, Screen.EditProfile)
    private val navigator = Navigator().apply { attach(backStack) }

    private fun fixture(
        cached: Boolean = true,
        failUpdates: Boolean = false,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): Fixture {
        val dataSource = FakeUserDataSource(mapOf("u1" to ada), failUpdates = failUpdates)
        val repository = UserRepository(dataSource)
        if (cached) repository.ingest(listOf(ada))
        val avatarLoader = FakeFileLoader(result = avatarUpload)

        return Fixture(
            EditProfileViewModel(repository, avatarLoader, navigator, savedStateHandle, "u1"),
            repository,
            dataSource,
            avatarLoader,
        )
    }

    private fun TestScope.collecting(viewModel: EditProfileViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun EditProfileViewModel.editing(): EditProfileUiState.Editing =
        uiState.value as EditProfileUiState.Editing

    private fun EditProfileViewModel.form(): EditProfileForm = editing().form

    @Test
    fun `form is seeded from the cached user`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        val form = viewModel.form()
        assertEquals("Ada Lovelace", form.nickname)
        assertEquals("36", form.age)
        assertEquals(GenderOption.WOMAN, form.gender)
        assertEquals("Mathematician & writer.", form.bio)
        assertNull(form.avatarUrl)
    }

    @Test
    fun `form loads from the data source when the cache is empty`() = runTest {
        val (viewModel, _, _) = fixture(cached = false)
        collecting(viewModel)

        assertEquals("Ada Lovelace", viewModel.form().nickname)
    }

    @Test
    fun `field changes update the form`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        viewModel.onEvent(EditProfileUiEvent.AgeChanged("37"))
        viewModel.onEvent(EditProfileUiEvent.GenderChanged(GenderOption.MAN))
        viewModel.onEvent(EditProfileUiEvent.BioChanged("Countess of Lovelace"))
        viewModel.onEvent(EditProfileUiEvent.AvatarPicked("content://media/picker/1"))

        val form = viewModel.form()
        assertEquals("Ada King", form.nickname)
        assertEquals("37", form.age)
        assertEquals(GenderOption.MAN, form.gender)
        assertEquals("Countess of Lovelace", form.bio)
        // A picked image previews via displayAvatar without overwriting the stored URL.
        assertEquals("content://media/picker/1", form.pickedAvatarUri)
        assertEquals("content://media/picker/1", form.displayAvatar)
    }

    @Test
    fun `age input keeps only digits and caps the length`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.AgeChanged("3a6.19"))

        assertEquals("361", viewModel.form().age)
    }

    @Test
    fun `form cannot save with a blank nickname or out-of-range age`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("   "))
        assertFalse(viewModel.form().canSave)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada"))
        viewModel.onEvent(EditProfileUiEvent.AgeChanged("999"))
        assertFalse(viewModel.form().canSave)

        viewModel.onEvent(EditProfileUiEvent.AgeChanged(""))
        assertTrue(viewModel.form().canSave)
    }

    @Test
    fun `form is not dirty until a field changes, and reverting clears it`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        assertFalse("seeded form matches the user", viewModel.editing().isDirty)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        assertTrue(viewModel.editing().isDirty)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada Lovelace"))
        assertFalse("reverting the edit is no longer dirty", viewModel.editing().isDirty)
    }

    @Test
    fun `Save persists the edited profile and navigates back`() = runTest {
        val (viewModel, repository, dataSource) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged(" Ada King "))
        viewModel.onEvent(EditProfileUiEvent.AgeChanged("37"))
        viewModel.onEvent(EditProfileUiEvent.BioChanged(""))
        viewModel.onEvent(EditProfileUiEvent.Save)

        assertEquals(
            "u1" to ProfileUpdate(
                nickname = "Ada King",
                age = 37,
                gender = "Woman",
                bio = null,
                avatar = null,
            ),
            dataSource.lastUpdate,
        )
        assertEquals("Ada King", repository.user("u1").first()?.nickname)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `Save reads the picked avatar and uploads it`() = runTest {
        val (viewModel, _, dataSource, avatarLoader) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.AvatarPicked("content://media/picker/42"))
        viewModel.onEvent(EditProfileUiEvent.Save)

        assertEquals("content://media/picker/42", avatarLoader.lastUri)
        assertEquals(avatarUpload, dataSource.lastUpdate?.second?.avatar)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `editing only other fields does not read or upload the avatar`() = runTest {
        val (viewModel, _, dataSource, avatarLoader) = fixture()
        collecting(viewModel)

        // Change every field except the avatar, then save.
        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        viewModel.onEvent(EditProfileUiEvent.AgeChanged("37"))
        viewModel.onEvent(EditProfileUiEvent.GenderChanged(GenderOption.MAN))
        viewModel.onEvent(EditProfileUiEvent.BioChanged("Countess of Lovelace"))
        viewModel.onEvent(EditProfileUiEvent.Save)

        assertNull("the picked image is never read", avatarLoader.lastUri)
        assertNull("no avatar is uploaded", dataSource.lastUpdate?.second?.avatar)
        assertEquals("Ada King", dataSource.lastUpdate?.second?.nickname)
    }

    @Test
    fun `Save is ignored while the form is invalid`() = runTest {
        val (viewModel, _, dataSource) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged(""))
        viewModel.onEvent(EditProfileUiEvent.Save)

        assertNull(dataSource.lastUpdate)
        assertEquals(Screen.EditProfile, backStack.last().screen)
    }

    @Test
    fun `a failed save surfaces the error and does not navigate away`() = runTest {
        val (viewModel, _, _) = fixture(failUpdates = true)
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.Save)

        val editing = viewModel.uiState.value as EditProfileUiState.Editing
        assertEquals(AppError.NoConnection, editing.saveError)
        assertFalse(editing.isSaving)
        assertEquals(Screen.EditProfile, backStack.last().screen)
    }

    @Test
    fun `GoBack shows discard confirmation when dirty`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        viewModel.onEvent(EditProfileUiEvent.GoBack)

        val editing = viewModel.uiState.value as EditProfileUiState.Editing
        assertTrue(editing.showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell, Screen.EditProfile), backStack.screens())
    }

    @Test
    fun `ConfirmDiscard pops the editor`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        viewModel.onEvent(EditProfileUiEvent.GoBack)
        viewModel.onEvent(EditProfileUiEvent.ConfirmDiscard)

        assertFalse((viewModel.uiState.value as EditProfileUiState.Editing).showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `DismissDiscard hides the dialog`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.NicknameChanged("Ada King"))
        viewModel.onEvent(EditProfileUiEvent.GoBack)
        viewModel.onEvent(EditProfileUiEvent.DismissDiscard)

        assertFalse((viewModel.uiState.value as EditProfileUiState.Editing).showDiscardConfirmation)
        assertEquals(listOf(Screen.Shell, Screen.EditProfile), backStack.screens())
    }

    @Test
    fun `GoBack pops immediately when clean`() = runTest {
        val (viewModel, _, _) = fixture()
        collecting(viewModel)

        viewModel.onEvent(EditProfileUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }
}
