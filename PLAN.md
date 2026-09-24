# Plan

Open work. Anything documented in [AGENTS.md](AGENTS.md) as existing behaviour has already landed —
check there before starting an item, since this file is the backlog and not a status report.

Ordered by severity. Each item names the files to start from, so it stands on its own.

## High — user-visible correctness

### A failed page load spins forever with no way to retry
`loadMore` swallows its error via `ignoreErrors` (`feed/ui/HomeViewModel.kt`) and `endReached` stays
false, so `LoadingMoreFooter` keeps spinning. `LoadMoreEffect`'s trigger is `distinctUntilChanged` on
a boolean that is still `true` (`common/ui/Pagination.kt`), so it won't re-fire until the user
scrolls away from the end and back. Offline at page 2 is an unresolvable spinner. Pagination needs an
error state and a "couldn't load more — tap to retry" footer.

### Mutations fail silently
Every mutation goes through `launchCatching`, which logs and discards (`common/util/StateFlows.kt`).
Defensible for a like toggle; wrong for **delete** (`post/ui/PostDetailViewModel.kt`) — the
confirmation dialog closes, nothing pops, nothing appears, and the user's tap simply does nothing.
Follow toggles and reports behave the same. There is no app-wide channel for "that didn't go
through"; at minimum delete and follow must surface failure.

## Medium — robustness

### Uploads buffer entire files in memory
`FileLoader.read` calls `readBytes()` (`common/data/files/FileLoader.kt`) and `FileUpload.asPart`
wraps the byte array (`common/data/network/MultipartParts.kt`), so ten photos plus a 25 MB video sit
on the heap across the whole multipart write. A custom `RequestBody` streaming from
`ContentResolver.openInputStream` holds constant memory. The size check *before* the read is right;
the read undoes it.

### ExoPlayer requests no audio focus
`ExoPlayer.Builder(appContext).build()` (`video/ui/VideoPlayback.kt`) never calls
`setAudioAttributes(attrs, handleAudioFocus = true)` or `setHandleAudioBecomingNoisy(true)`. Feed
videos play over the user's music without pausing it and keep playing out of the speaker when
headphones are unplugged. For an autoplaying feed this is table stakes.

## Low priority

### Rename Data Sources to Interactors?
They don't just source data, they interact with the backend to make it perform actions.
Thus, an Interactor might be a better name.

### Better sample data
Add more posts and comments. At least two pages of posts to test feed.

### In dark mode, when transitioning between screens, it flashes with white
Are the visuals of the predictive back gesture customizable? Is that what this is?

## Code quality

- **`EditProfileViewModel` uses the array-overload `combine` with unchecked casts**
  (`args[0] as EditProfileForm?`, `profile/ui/EditProfileViewModel.kt`) while `ProfileViewModel` solved
  the same arity problem with a typed pairing class (`LazyTabs`). The typed approach is the one worth
  showing off.
- **`Modifier.composed` in `debouncedClickable`** (`designsystem/components/ClickDebounce.kt`) — the Compose team
  discourages `composed` because it defeats modifier skipping and reuse; `Modifier.Node` is the
  current answer.
- **`derivedIds` re-filters and re-sorts the whole entity store on every emission**
  (`profile/data/ProfileRepository.kt`) — O(N log N) per emission per subscribed tab. Fine at demo
  scale; worth knowing where the cliff is.
- **Following kept the shape likes moved away from.** `POST /users/:id/follow` is still a
  server-side toggle, so a timeout-retry can flip it twice, and `UserRepository.toggleFollow` still
  waits out the round trip. The like/bookmark change is the template: an idempotent
  `PUT follow=true/false` plus an optimistic apply-then-reconcile over the user store.

## Doc accuracy

- **A KDoc describes machinery that doesn't exist.** `settings/data/DataStoreSettingsRepository.kt`
  describes "a one-time migration from the legacy SharedPreferences file" that appears nowhere — the
  store is created with no migrations (`app/di/DataModule.kt`). In a codebase whose comments are this
  load-bearing, a comment describing absent machinery is worse than none.
- **The README should state that `X-User-Id` is trust-the-client authentication.** It's a deliberate
  no-sign-in choice and fine for a sample, but impersonation is one header away and a reviewer should
  see that we know it.
