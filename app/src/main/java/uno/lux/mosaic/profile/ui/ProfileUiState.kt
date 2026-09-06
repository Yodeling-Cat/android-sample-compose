package uno.lux.mosaic.profile.ui

import uno.lux.mosaic.app.util.AppError

/** The profile screen's state: loading, the loaded profile data, or an unknown user. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(
        val error: AppError,
    ) : ProfileUiState

    /** [isCurrentUser] is true when this is the signed-in user's own profile. */
    data class Loaded(
        val data: ProfileScreenData,
        val isCurrentUser: Boolean,
    ) : ProfileUiState

    data object NotFound : ProfileUiState
}
