package uno.lux.mosaic.home.ui

import uno.lux.mosaic.app.util.AppError
import uno.lux.mosaic.post.ui.PostCardData

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Error(
        val error: AppError,
    ) : HomeUiState

    data class Feed(
        val posts: List<PostCardData>,
        val endReached: Boolean,
        val refreshError: AppError? = null,
    ) : HomeUiState
}
