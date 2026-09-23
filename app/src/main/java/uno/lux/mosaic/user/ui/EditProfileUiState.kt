package uno.lux.mosaic.user.ui

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import uno.lux.mosaic.R
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

enum class GenderOption(
    val storedValue: String,
    @get:StringRes val labelRes: Int,
) {
    MAN("Man", R.string.gender_man),
    WOMAN("Woman", R.string.gender_woman),
    ;

    companion object {
        fun fromStored(value: String?): GenderOption? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/** Mirrors the server's validation. */
val EditProfileAgeRange = 13..120

@Serializable
data class EditProfileForm(
    val userId: UserId,
    val nickname: String,
    val age: String,
    val gender: GenderOption?,
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
        gender = gender?.storedValue,
        bio = bio.trim().ifEmpty { null },
        avatar = avatar,
    )

    companion object {
        fun from(user: User) = EditProfileForm(
            userId = user.id,
            nickname = user.nickname,
            age = user.age?.toString().orEmpty(),
            gender = GenderOption.fromStored(user.gender),
            bio = user.bio.orEmpty(),
            avatarUrl = user.avatarUrl,
        )
    }
}

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
