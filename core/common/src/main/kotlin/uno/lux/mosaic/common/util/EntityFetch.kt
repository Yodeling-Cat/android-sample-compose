package uno.lux.mosaic.common.util

/**
 * What an entity the store does not hold means, on a page that resolves one by id. [Done] is not
 * "found": the page still reads the store, and only an entity absent after [Done] is not found.
 */
sealed interface EntityFetch {
    data object Pending : EntityFetch

    data object Done : EntityFetch

    data class Failed(
        val error: AppError,
    ) : EntityFetch
}
