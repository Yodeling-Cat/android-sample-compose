package uno.lux.mosaic.user.ui

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import uno.lux.mosaic.R
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

/**
 * The gender choices the profile editor offers. [storedValue] is the canonical value the
 * backend accepts and stores; [labelRes] is what the user sees, so the two can diverge under
 * localization without changing the data contract.
 */
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

/** Ages the editor accepts; mirrors the backend's validation so errors surface before a save. */
val EditProfileAgeRange = 13..120

/**
 * The editable form fields, seeded once from the loaded [User]. [age] stays the raw digit
 * string the user typed; it only becomes an [Int] in [toProfileUpdate]. [avatarUrl] is the
 * current server avatar; [pickedAvatarUri] is a newly chosen local image (a `content://` URI)
 * not yet uploaded — [displayAvatar] prefers it for the live preview. [userId] is carried
 * along, unedited, only so the avatar preview can key its fallback gradient on it.
 *
 * [Serializable] so in-progress edits survive process death — see [uno.lux.mosaic.util.saveDraft].
 */
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

    /** [avatar] is the uploaded bytes of [pickedAvatarUri], resolved by the ViewModel on save. */
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

/** The edit-profile screen's state: loading the profile, a failed load, or the live form. */
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
