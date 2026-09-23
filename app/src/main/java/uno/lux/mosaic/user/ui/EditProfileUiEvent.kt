package uno.lux.mosaic.user.ui

sealed interface EditProfileUiEvent {

    data class NicknameChanged(
        val value: String,
    ) : EditProfileUiEvent

    data class AgeChanged(
        val value: String,
    ) : EditProfileUiEvent

    data class GenderChanged(
        val gender: GenderOption,
    ) : EditProfileUiEvent

    data class BioChanged(
        val value: String,
    ) : EditProfileUiEvent

    data class AvatarPicked(
        val uri: String,
    ) : EditProfileUiEvent

    data object Save : EditProfileUiEvent

    data object Retry : EditProfileUiEvent

    data object GoBack : EditProfileUiEvent

    data object DismissDiscard : EditProfileUiEvent

    data object ConfirmDiscard : EditProfileUiEvent
}
