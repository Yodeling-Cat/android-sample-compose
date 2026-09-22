package uno.lux.mosaic.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory [DataStore] of [Preferences], for driving the real [DataStoreSettingsRepository]
 * over the real preference keys.
 *
 * It stands in for the file, not for the repository: what is faked is only where the bytes live,
 * so the mapping under test — [Preferences] keys to domain types and back — is the production one.
 *
 * Writes are serialized, as [DataStore.updateData] promises, so a transform sees what the previous
 * one returned rather than the value it started from.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())
    private val writeLock = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        writeLock.withLock {
            transform(state.value).also { state.value = it }
        }
}
