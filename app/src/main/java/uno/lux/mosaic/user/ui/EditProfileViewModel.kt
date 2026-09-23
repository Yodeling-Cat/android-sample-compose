package uno.lux.mosaic.user.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.app.di.CurrentUserId
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.common.data.files.FileLoader
import uno.lux.mosaic.common.ui.ignoreErrors
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.common.util.restoreDraft
import uno.lux.mosaic.common.util.saveDraft
import uno.lux.mosaic.common.util.stateInWhileSubscribed
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.UserId
import javax.inject.Inject

/**
 * Drives the signed-in user's profile editor. The form is seeded from [UserRepository]'s
 * cached user as a **one-time snapshot** — deliberately not kept in sync afterwards, so a
 * concurrent cache refresh can't clobber in-progress edits. Saving goes back through the
 * repository, which replaces the cached entry, making the change instantly visible on every
 * screen observing the user; once the save lands, the editor pops itself off the back stack
 * through the injected [Navigator] (a failed save stays put and surfaces its error).
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val fileLoader: FileLoader,
    private val navigator: Navigator,
    private val savedStateHandle: SavedStateHandle,
    @param:CurrentUserId private val userId: UserId,
) : ViewModel() {

    // Edits in progress are the one thing here that can't be fetched again, so they are saved.
    private val _form = MutableStateFlow(savedStateHandle.restoreDraft<EditProfileForm>(DRAFT_KEY))
    private val _initialForm = MutableStateFlow<EditProfileForm?>(null)
    private val _loadError = MutableStateFlow<AppError?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _showDiscardConfirmation = MutableStateFlow(false)
    private val _saveError = MutableStateFlow<AppError?>(null)

    private val isDirty: Boolean
        get() {
            val form = _form.value ?: return false
            val initial = _initialForm.value ?: return false
            return form != initial
        }

    val uiState: StateFlow<EditProfileUiState> = combine(
        _form,
        _initialForm,
        _loadError,
        _isSaving,
        _showDiscardConfirmation,
        _saveError,
    ) { args ->
        val form = args[0] as EditProfileForm?
        val initialForm = args[1] as EditProfileForm?
        val loadError = args[2] as AppError?
        val isSaving = args[3] as Boolean
        val showDiscardConfirmation = args[4] as Boolean
        val saveError = args[5] as AppError?

        when {
            form != null -> EditProfileUiState.Editing(
                form = form,
                isDirty = form != initialForm,
                isSaving = isSaving,
                showDiscardConfirmation = showDiscardConfirmation,
                saveError = saveError,
            )

            loadError != null -> EditProfileUiState.Error(loadError)

            else -> EditProfileUiState.Loading
        }
    }.stateInWhileSubscribed(viewModelScope, EditProfileUiState.Loading)

    private var loadJob: Job? = null
    private var saveJob: Job? = null

    init {
        savedStateHandle.saveDraft(DRAFT_KEY) { _form.value }
        retry()
    }

    fun onEvent(event: EditProfileUiEvent) {
        when (event) {
            is EditProfileUiEvent.NicknameChanged -> {
                updateForm { it.copy(nickname = event.value) }
            }

            is EditProfileUiEvent.AgeChanged -> {
                updateForm { form ->
                    form.copy(age = event.value.filter { it.isDigit() }.take(3))
                }
            }

            is EditProfileUiEvent.GenderChanged -> {
                updateForm { it.copy(gender = event.gender) }
            }

            is EditProfileUiEvent.BioChanged -> {
                updateForm { it.copy(bio = event.value) }
            }

            is EditProfileUiEvent.AvatarPicked -> {
                updateForm { it.copy(pickedAvatarUri = event.uri) }
            }

            EditProfileUiEvent.Save -> {
                save()
            }

            EditProfileUiEvent.Retry -> {
                retry()
            }

            EditProfileUiEvent.GoBack -> {
                goBack()
            }

            EditProfileUiEvent.DismissDiscard -> {
                _showDiscardConfirmation.value = false
            }

            EditProfileUiEvent.ConfirmDiscard -> {
                _showDiscardConfirmation.value = false
                navigator.goBack()
            }
        }
    }

    private fun retry() = launchIfIdle(::loadJob) { load() }

    private suspend fun load() {
        _loadError.value = null

        ignoreErrors(_loadError) {
            // The cache normally already holds the user (the editor opens from their loaded
            // profile); it's only empty when the screen is restored after process death.
            if (userRepository.user(userId).first() == null) {
                userRepository.refresh(userId)
            }
            val user = checkNotNull(userRepository.user(userId).first()) {
                "Signed-in user '$userId' not found"
            }

            // The pristine snapshot always comes from the server, so the dirty check compares
            // against what is actually stored — but restored edits win over it, or a restart
            // would quietly undo them.
            val stored = EditProfileForm.from(user)
            _initialForm.value = stored
            if (_form.value == null) _form.value = stored
        }
    }

    private fun save() {
        val form = _form.value ?: return
        if (!form.canSave) return

        launchIfIdle(::saveJob) {
            _saveError.value = null
            _isSaving.value = true
            try {
                ignoreErrors(_saveError) {
                    // Read the picked image into upload bytes only if one was chosen; otherwise
                    // the current avatar is left untouched.
                    val avatar = form.pickedAvatarUri?.let { fileLoader.read(it) }
                    userRepository.updateProfile(userId, form.toProfileUpdate(avatar))
                    navigator.goBack()
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun goBack() {
        if (isDirty) {
            _showDiscardConfirmation.value = true
        } else {
            navigator.goBack()
        }
    }

    private fun updateForm(transform: (EditProfileForm) -> EditProfileForm) {
        _form.update { form -> form?.let(transform) }
    }
}

/** Where in-progress edits are kept in the entry's saved state. */
private const val DRAFT_KEY = "edit_profile_draft"
