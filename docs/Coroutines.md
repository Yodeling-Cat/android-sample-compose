# Coroutines

## 1. The one-sentence answer

A coroutine is a **suspendable computation**: a block of work that can pause at defined points and resume later, without blocking the thread it was running on.

The interview follow-up is always *"so how is that different from a thread?"* A thread is an OS resource — expensive, preemptively scheduled, a fixed stack. A coroutine is an object on the heap. One thread can run thousands of coroutines; a suspended coroutine occupies no thread at all. That is the whole value proposition: **concurrency without the cost of a thread per unit of work.**

## 2. `suspend` — what the compiler actually does

`suspend` is not a threading instruction. It is a marker that says "this function may suspend, so it can only be called from somewhere that knows how to resume it."

The compiler rewrites a `suspend fun` using **CPS (continuation-passing style)**:

- It adds a hidden `Continuation` parameter — the callback holding "what to do when this resumes."
- It compiles the function body into a **state machine**: each suspension point becomes a label, and locals that live across a suspension point become fields on the state machine object.
- The function returns either its value, or the sentinel `COROUTINE_SUSPENDED`.

Two consequences worth stating out loud in an interview:

1. **`suspend` alone does not move work off the main thread.** A `suspend fun` that runs a tight CPU loop will freeze the UI exactly like a normal function. Off-thread work needs a **dispatcher**, not the keyword.
2. **There is no reflection or magic and no extra thread per call.** Suspension is a return, and resumption is a call. That is why coroutines are cheap.

## 3. Structured concurrency — the actual big idea

Every coroutine has a parent. A parent cannot complete until its children complete; cancelling a parent cancels every child; a failing child (by default) cancels its parent and its siblings. There are no orphans and nothing leaks.

This is why `GlobalScope` is effectively banned — it has no parent, so nothing can cancel it and nobody waits for it.

`coroutineScope { }` is the building block: it suspends until all children finish, and it re-throws the first failure. `ProfileViewModel.load` uses it to fan out four requests and wait for the lot:

```kotlin
private suspend fun load() {
    _loadError.value = null

    ignoreErrors(_loadError) {
        coroutineScope {
            launch { userRepository.refresh(userId) }
            launch { profileRepository.refresh(userId) }
            launch { bookmarks.refreshIfLoaded() }
            launch { likes.refreshIfLoaded() }
        }
    }

    _hasLoaded.value = true
}
```

`_hasLoaded` is set on the line after the block — and that line cannot run until all four requests are done. Structured concurrency is what makes that sequencing free.

### What order do those four run in?

Start order is **source order**. Completion order is **whatever the network decides**.

`launch` returns a `Job` immediately and never blocks, so all four lines execute in microseconds and the block reaches its closing brace almost at once — then suspends *there*, waiting for its children. What "start" means depends on the dispatcher, and the two cases behave differently:

- **`Dispatchers.Main.immediate`, already on the main thread.** This is the case here: `viewModelScope` supplies that dispatcher and `coroutineScope` inherits it. No dispatch is needed, so each `launch` runs its body **inline, up to that body's first suspension point**, before the next line runs. Note that `refreshIfLoaded` returns early for a tab nobody has opened — so children three and four can finish before the first request has even been sent.
- **A dispatching dispatcher (`Default`, `IO`, `StandardTestDispatcher`).** All four are queued and none of the bodies run yet. They start as threads pick them up, in no guaranteed order, genuinely in parallel.

Both give the same observable outcome. The difference is worth knowing because the first case is why a `launch` in a ViewModel sometimes appears to run synchronously.

**Concurrent is not parallel.** On `Main.immediate` all four coroutines live on one thread and interleave at suspension points, so only one is ever running. The requests are still in flight simultaneously, because Retrofit hands each to OkHttp's pool and suspends the coroutine — the main thread is free throughout. Concurrency is about structure (several things in progress), parallelism is about execution (several things running at once). This code is concurrent on a single thread, and the parallelism happens elsewhere.

**Nothing is returned.** All four are `suspend fun … : Unit`; each writes into its own store, and the ViewModel `combine`s those stores, so the screen updates as each result lands rather than waiting for the slowest. That is precisely why these are `launch` and not `async` — see section 6.

**`coroutineScope` vs `supervisorScope`**: in `coroutineScope`, one child's failure cancels its siblings. In `supervisorScope`, children fail independently. Pick `coroutineScope` when the results are one unit of work (the four refreshes above — a half-loaded profile is not worth showing) and `supervisorScope` when they are independent.

`viewModelScope` is built on a `SupervisorJob` plus `Dispatchers.Main.immediate`, and is cancelled in `onCleared()`. That cancellation is the reason a ViewModel never leaks a request past the screen.

## 4. Dispatchers and main-safety

A dispatcher decides which thread a coroutine resumes on.

| Dispatcher | Backed by | Use for |
|---|---|---|
| `Main` | Android UI looper | Touching UI / state read by UI |
| `Main.immediate` | same | As above, without a re-dispatch if already on Main |
| `IO` | elastic pool (64+ threads) | **Blocking** calls — disk, files, sockets, JDBC |
| `Default` | pool sized to CPU cores | **CPU-bound** work — parsing, sorting, image math |
| `Unconfined` | caller's thread | Testing and rare edge cases; do not reach for it |

`withContext(dispatcher) { }` switches for a block and switches back. The convention is **main-safety**: a `suspend fun` is responsible for its own threading, so the caller never has to know. That is why data sources here look like this:

```kotlin
override suspend fun read(uri: String): FileUpload = withContext(Dispatchers.IO) {
    // genuinely blocking: ContentResolver + InputStream
}
```

A nuance worth knowing, because it makes a good interview answer: **Retrofit `suspend` functions are already main-safe.** Retrofit enqueues on OkHttp's own dispatcher and resumes your coroutine when the response lands — it never blocks the calling thread. So `withContext(Dispatchers.IO)` around a bare Retrofit call is redundant (harmless, but it says you think the call blocks). It *is* required around `FileLoader` and `VideoMetadataReader`, which really do block. The rule: **`Dispatchers.IO` is for blocking APIs, not for "things that are slow."**

## 5. Cancellation is cooperative

Cancelling a coroutine sets its `Job` to cancelling; it does not kill a thread. The coroutine actually stops at its next **suspension point**, because every suspending function in the stdlib checks for cancellation on resume and throws `CancellationException`.

Three things follow:

- **A tight CPU loop with no suspension point is uncancellable.** Call `ensureActive()` or `yield()` inside it, or check `isActive`.
- **`CancellationException` must be re-thrown, never swallowed.** It is an ordinary `Exception` on the JVM, so a blanket `catch (e: Exception)` will eat it and break structured concurrency — the parent then thinks the child finished normally. This is exactly what `catchErrors` guards:

```kotlin
suspend fun catchErrors(onError: (Exception) -> Unit = {}, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e)
        onError(e)
    }
}
```

- **Cleanup in `finally` cannot suspend** in a cancelled coroutine — a suspension there throws immediately. If cleanup must suspend, wrap it in `withContext(NonCancellable)`.

Cancellation is also a *feature*, not just a safety net. `dropReport` cancels an in-flight report when the dialog closes, so a response nobody is waiting for cannot settle into the *next* dialog:

```kotlin
fun dropReport(jobRef: KMutableProperty0<Job?>, setState: (ReportSendState) -> Unit) {
    jobRef.get()?.cancel()
    jobRef.set(null)
    setState(ReportSendState.IDLE)
}
```

And cancellation is not the same as failure. `PostRepository.toggleLike` treats them differently on purpose — a cancelled like keeps its optimistic value, because the request is already on the wire; only a real failure reverts.

## 6. `launch` vs `async`, and how exceptions travel

| | `launch` | `async` |
|---|---|---|
| Returns | `Job` | `Deferred<T>` |
| For | fire-and-forget | a value you will `await()` |
| Failure surfaces | immediately, up the parent chain | on `await()` — **and** immediately to the parent, if the parent is a `coroutineScope` |

The classic gotcha: **`async` at the root of a scope swallows an exception until you `await`.** Inside `coroutineScope`, it does not — the failure cancels the scope right away.

Parallel decomposition is `async` + `await`, as in `NetworkProfileDataSource.refresh`:

```kotlin
override suspend fun refresh(userId: UserId): ProfileRefreshData = withContext(Dispatchers.IO) {
    val statsDeferred = async { api.getProfileStats(userId).data }
    val postsDeferred = async { api.getUserPosts(userId) }

    ProfileRefreshData(
        postsCount = statsDeferred.await().postsCount,
        page = postsDeferred.await().toPage(),
    )
}
```

Both requests start before either is awaited — that is the point. Awaiting the first on the line that starts it would serialize them.

`CoroutineExceptionHandler` only works on a *root* coroutine (installed on the scope, or on a `launch` whose parent is a `SupervisorJob`). Installing one on a child is a no-op — the failure has already gone to the parent.

## 7. Flow

**A `Flow` is cold**: the block does not run until someone collects, and each collector gets its own run. `StateFlow` and `SharedFlow` are **hot**: they exist and emit whether or not anyone is listening.

| | `StateFlow` | `SharedFlow` |
|---|---|---|
| Initial value | required | none |
| Replay | always 1 (the current value) | configurable |
| Dedup | yes — conflates by `equals` | no |
| Good for | UI state | one-shot events |

`StateFlow`'s equality-based conflation is why UI state should be a `data class`: a `copy` that changes nothing is not re-emitted.

**Exposing state from a ViewModel** is the standard question. The answer here:

```kotlin
fun <T> Flow<T>.stateInWhileSubscribed(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialValue)
```

`WhileSubscribed(5000)` keeps the upstream alive for 5 seconds after the last collector leaves. That number is chosen to span a configuration change: rotate the screen and the upstream is *not* torn down and restarted. Leave for a minute and it is. The alternatives are `Eagerly` (collects forever, does work nobody reads) and `Lazily` (starts on first collector, never stops).

The collector side must match: `collectAsStateWithLifecycle()` stops collecting below `STARTED`. Plain `collectAsState()` keeps collecting while the app is backgrounded, which is what makes `WhileSubscribed` pointless.

Other operators worth naming: `flowOn` (changes the dispatcher of everything **upstream** of it), `combine` (re-emits when *any* source emits), `distinctUntilChanged`, `conflate`, `debounce`, and `flatMapLatest` (cancels the previous inner flow — the search-as-you-type operator).

## 8. Shared mutable state

Coroutines do not make race conditions go away. Two rules cover most of it:

1. **Confine the state to one place** and mutate it atomically. `MutableStateFlow.update { }` is a compare-and-set loop, so this is safe under concurrent callers:

```kotlin
_entities.update { it + (postId to updated) }
```

2. **Re-read before you write.** Reading an entity, suspending, then writing that stale snapshot back is a real bug this repo hit: a refresh that landed mid-flight got overwritten by the older copy. `PostRepository.updateEntity` re-reads inside the update and touches only the fields it owns.

Related, and a good thing to have an opinion about: **out-of-order responses.** Two like taps in flight should settle on the *second* one. The guard is a predicate checked at reconcile time:

```kotlin
val stillOurs = { post: Post -> post.isLiked == liked }
```

If a newer tap has already moved the value, the older response is dropped instead of applied. For the other case — where the second call should simply not happen — `launchIfIdle` single-flights it:

```kotlin
fun ViewModel.launchIfIdle(jobRef: KMutableProperty0<Job?>, block: suspend () -> Unit) {
    if (jobRef.get()?.isActive == true) return
    jobRef.set(viewModelScope.launch { block() })
}
```

`Mutex` (`withLock`) exists for the cases neither covers. It is a suspending lock, so it never blocks a thread. Prefer confinement first.

## 9. Testing

`runTest` gives you a **virtual clock**: `delay(10_000)` returns instantly, so a test of a timeout takes microseconds.

- **`StandardTestDispatcher`** queues coroutines; nothing runs until you call `advanceUntilIdle()` or `runCurrent()`. Use it when you need to assert on an intermediate state — the loading spinner *before* the response lands.
- **`UnconfinedTestDispatcher`** runs eagerly to the first suspension point. Use it when you only care about the end state. It is the default here.
- **`backgroundScope`** is for collectors that never complete. Launching an infinite `collect` in the test scope hangs `runTest` forever; `backgroundScope` is cancelled when the test body ends.
- **`Dispatchers.Main` does not exist on the JVM**, so anything using `viewModelScope` needs it replaced. That is `MainDispatcherRule` in `testing/`, which calls `Dispatchers.setMain` / `resetMain` around each test.
- **Inject dispatchers rather than hardcoding them** when a test needs to control them. Test doubles here are hand-written fakes; no mocking framework.

## 10. Rapid-fire

- **Coroutine vs thread?** A coroutine is a heap object scheduled by a library; a thread is an OS resource scheduled by the kernel. Many coroutines share one thread.
- **Does `suspend` make it async?** No. It makes the function *suspendable*. Dispatchers make it off-thread.
- **Why not `GlobalScope`?** No parent, so no cancellation and no completion guarantee. It leaks.
- **`launch` vs `async`?** Side effect vs value. `async` without `await` is usually a bug.
- **`Job` vs `SupervisorJob`?** Under a plain `Job`, one child's failure cancels its siblings. Under a `SupervisorJob`, it does not.
- **Why did my `catch` break cancellation?** `CancellationException` is an `Exception`. Re-throw it.
- **`Dispatchers.IO` vs `Default`?** Blocking vs CPU-bound. The IO pool is large because its threads are mostly parked.
- **Cold vs hot flow?** Cold restarts per collector and is inert without one; hot exists independently.
- **`WhileSubscribed(5000)`?** Survives a configuration change, stops for a real backgrounding.
- **How do you cancel a CPU loop?** `ensureActive()` or `yield()` inside it — otherwise nothing can.
