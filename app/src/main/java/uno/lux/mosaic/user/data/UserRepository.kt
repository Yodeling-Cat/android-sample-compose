package uno.lux.mosaic.user.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

/**
 * Single source of truth for user identity.
 */
class UserRepository(
    private val dataSource: UserDataSource,
) {
    private val _cache = MutableStateFlow<Map<UserId, User>>(emptyMap())

    /**
     * The full map of all currently known users keyed by id.
     */
    val users: StateFlow<Map<UserId, User>> = _cache.asStateFlow()

    /** Streams the cached [User] for a given id, emitting `null` when the id is unknown. */
    fun user(userId: UserId): Flow<User?> = _cache.map { it[userId] }

    /**
     * Merges a batch of users (e.g. sideloaded from the feed response) into the cache.
     *
     * Entries replace wholesale rather than merging field-wise, which is safe only because the API
     * serves one user projection: a sideloaded author is complete.
     */
    fun ingest(users: List<User>) {
        if (users.isEmpty()) return
        _cache.update { current -> current + users.associateBy { it.id } }
    }

    suspend fun refresh(userId: UserId) {
        val user = dataSource.fetch(userId) ?: return
        _cache.update { it + (userId to user) }
    }

    suspend fun updateProfile(userId: UserId, profileUpdate: ProfileUpdate) {
        val user = dataSource.update(userId, profileUpdate)
        _cache.update { it + (userId to user) }
    }

    suspend fun toggleFollow(userId: UserId) {
        val user = _cache.value[userId] ?: return
        val updated = dataSource.toggleFollow(user)
        _cache.update { it + (userId to updated) }
    }
}
