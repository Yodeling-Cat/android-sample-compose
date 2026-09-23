package uno.lux.mosaic.profile.ui

import androidx.compose.runtime.Immutable
import uno.lux.mosaic.common.util.AppError

@Immutable
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(
        val error: AppError,
    ) : ProfileUiState

    data class Loaded(
        val data: ProfileScreenData,
        val isCurrentUser: Boolean,
    ) : ProfileUiState

    data object NotFound : ProfileUiState
}
