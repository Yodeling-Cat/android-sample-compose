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
 *
 * [user] streams the cached [User] for a given id, emitting `null` when the id is unknown.
 * [users] streams the full map of all currently known users keyed by id — used by list
 * screens (the feed, a profile's posts tab) to resolve author names without a per-row lookup.
 * [ingest] merges a batch of users (e.g. sideloaded from the feed response) into the cache.
 * Entries replace wholesale rather than merging field-wise, which is safe only because the API
 * serves one user projection: a sideloaded author is as complete as a fetched profile. Were a
 * partial projection reintroduced, this write would silently blank profile detail already held.
 * [refresh] forces a re-fetch of a single entry from the backing source, replacing the cache.
 * [updateProfile] persists edited profile fields and replaces the cached entry with the
 * result, so every observing screen sees the change immediately.
 * [toggleFollow] flips whether the current user follows the given user and replaces the cached
 * entry with the server's result (new follow state + follower count), so the change propagates
 * to every screen showing that user — the profile, the feed author header, and so on.
 *
 * Where the data comes from — in-memory sample data vs. a live network call — is decided by
 * [UserDataSource].
 */
class UserRepository(
    private val dataSource: UserDataSource,
) {
    private val _cache = MutableStateFlow<Map<UserId, User>>(emptyMap())
    val users: StateFlow<Map<UserId, User>> = _cache.asStateFlow()

    fun user(userId: UserId): Flow<User?> = _cache.map { it[userId] }

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
