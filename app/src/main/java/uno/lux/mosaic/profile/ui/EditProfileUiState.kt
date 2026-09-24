package uno.lux.mosaic.profile.ui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.user.data.domain.Gender
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

/** Mirrors the server's validation. */
val EditProfileAgeRange = 13..120

@Serializable
data class EditProfileForm(
    val userId: UserId,
    val nickname: String,
    val age: String,
    val gender: Gender?,
    val bio: String,
    val avatarUrl: String?,
    val pickedAvatarUri: String? = null,
) {
    val displayAvatar: String?
        get() = pickedAvatarUri ?: avatarUrl

    val isAgeValid: Boolean
        get() {
            if (age.isEmpty()) return true
            val value = age.toIntOrNull() ?: return false

            return value in EditProfileAgeRange
        }

    val canSave: Boolean
        get() = nickname.isNotBlank() && isAgeValid

    fun toProfileUpdate(avatar: FileUpload?) = ProfileUpdate(
        nickname = nickname.trim(),
        age = age.toIntOrNull(),
        gender = gender,
        bio = bio.trim().ifEmpty { null },
        avatar = avatar,
    )

    companion object {
        fun from(user: User) = EditProfileForm(
            userId = user.id,
            nickname = user.nickname,
            age = user.age?.toString().orEmpty(),
            gender = user.gender,
            bio = user.bio.orEmpty(),
            avatarUrl = user.avatarUrl,
        )
    }
}

@Immutable
sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState

    data class Error(
        val error: AppError,
    ) : EditProfileUiState

    data class Editing(
        val form: EditProfileForm,
        val isDirty: Boolean,
        val isSaving: Boolean,
        val showDiscardConfirmation: Boolean,
        val saveError: AppError?,
    ) : EditProfileUiState
}
