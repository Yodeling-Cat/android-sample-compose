package uno.lux.mosaic.profile.ui

import uno.lux.mosaic.app.util.AppError

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
