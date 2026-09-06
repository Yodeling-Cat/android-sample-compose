package uno.lux.mosaic.app.util

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/*
 * Persisting a **draft** — the part of a screen's state the user typed — across process death.
 *
 * The back stack restores which page was open; everything a ViewModel holds is built again from
 * scratch. A page that fetches can simply re-fetch (see `PostDetailViewModel`), but nobody can
 * re-derive a half-written post, so a form's own state is the one thing that has to be *saved*.
 * [SavedStateHandle] is the mechanism: each back-stack entry has its own, tied to the same
 * instance state the [uno.lux.sample.app.navigation.Screen] keys ride in.
 *
 * [saveDraft] registers where to *read* the draft from rather than pushing every edit into the
 * handle: the platform pulls it at most once per save, so typing costs nothing and there is no
 * collector to own. That is why this takes a `() -> T` and not a `Flow<T>`.
 */

/** What [saveDraft] left under [key] in a previous process, or null on a fresh start. */
inline fun <reified T> SavedStateHandle.restoreDraft(key: String): T? =
    restoreDraft(serializer(), key)

fun <T> SavedStateHandle.restoreDraft(serializer: KSerializer<T>, key: String): T? =
    get<SavedState>(key)?.let { decodeFromSavedState(serializer, it) }

/**
 * Registers [draft] as what to store under [key] whenever the system saves state, for
 * [restoreDraft] to find after a restart. The registration lives on the handle, which the
 * back-stack entry owns, so it goes when the entry is popped.
 */
inline fun <reified T> SavedStateHandle.saveDraft(key: String, noinline draft: () -> T) =
    saveDraft(serializer(), key, draft)

fun <T> SavedStateHandle.saveDraft(
    serializer: KSerializer<T>,
    key: String,
    draft: () -> T,
) = setSavedStateProvider(key) { encodeToSavedState(serializer, draft()) }
