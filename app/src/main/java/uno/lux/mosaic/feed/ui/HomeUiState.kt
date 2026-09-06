package uno.lux.mosaic.feed.ui

import uno.lux.mosaic.app.util.AppError
import uno.lux.mosaic.post.ui.PostCardData

/**
 * Exhaustive state for the feed screen, rendered by a stateless `HomeScreen`. Modeled as
 * a sealed interface so the `when` over it is checked at compile time.
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** Nothing to show and the load failed — the whole screen is the error. */
    data class Error(
        val error: AppError,
    ) : HomeUiState

    /**
     * [endReached] is true when the backend has no more posts beyond [posts].
     *
     * [refreshError] carries a load that failed *over* these posts — a pull-to-refresh in a
     * tunnel. The feed being readable outranks the failure, so it is reported transiently (a
     * snackbar) instead of replacing the content the user was already looking at; [Error] is
     * reserved for having nothing to show at all. It stands only until the screen has announced
     * it and calls `HomeActions.onRefreshErrorShown` — state, but state with a lifetime, which is
     * what keeps a rebuilt composition from repeating a failure the user has already read.
     */
    data class Feed(
        val posts: List<PostCardData>,
        val endReached: Boolean,
        val refreshError: AppError? = null,
    ) : HomeUiState
}
