# Compose UI

## 1. The one-sentence answer

Compose is a **declarative** UI toolkit: you write a function that turns state into UI, and the runtime re-runs that function whenever the state it read changes.

The interview follow-up is always *"so how is that different from Views?"* A `View` is a mutable object you hold a reference to and imperatively mutate — `textView.setText(...)`, `button.setVisibility(GONE)`. The screen is the accumulated result of every mutation you remembered to perform. A composable owns no object and mutates nothing; it *describes* the UI for the state it was handed. The screen is a pure function of state, so there is no "forgot to reset the visibility" bug, because there is nothing to reset.

Everything hard about Compose follows from the one question this creates: **if the UI is a function of state, where does the state live, and what is it allowed to survive?**

## 2. Composition, layout, draw — the three phases

A frame runs three phases, and knowing which phase your state is read in is most of what performance work in Compose amounts to.

| Phase | What it does | Cost of re-running |
|---|---|---|
| **Composition** | Runs `@Composable` functions, builds and updates the tree of nodes | Highest — allocations, `remember` lookups, subtree invalidation |
| **Layout** | Measures and places each node | Medium |
| **Draw** | Issues draw commands onto the canvas | Lowest |

A state read *during composition* invalidates the composition. A state read inside a `Modifier.graphicsLayer { }` or a `drawBehind { }` lambda invalidates only that phase — the composable does not re-run at all. This is why the like animation in [PostContent.kt:371](../app/src/main/java/uno/lux/mosaic/post/ui/PostContent.kt#L371) reads its `Animatable` inside `graphicsLayer` rather than passing `scale.value` down as a parameter:

```kotlin
return graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}
```

400 ms of pop animation costs zero recompositions. Written as `Modifier.scale(scale.value)`, every frame would re-run the enclosing composable.

**Recomposition is not re-rendering.** Compose recomposes *scopes*, not screens: only the composable functions that actually read the changed state re-run, and even those skip when their parameters are unchanged. Sections 6 to 9 are about making that granularity work for you.

## 3. State — the full menu

This is the section worth memorising. Every tool below answers a different question.

### `mutableStateOf` — the observable box

```kotlin
val state: MutableState<Int> = mutableStateOf(0)
```

`MutableState<T>` is an observable holder. Reading `.value` inside a composable **registers that composable's scope as a reader**; writing `.value` invalidates every registered reader. The snapshot system does the bookkeeping — there are no listeners to register and no `LiveData` to observe.

`by` delegation reads better and compiles to the same thing:

```kotlin
var scale by remember { mutableFloatStateOf(1f) }   // read `scale`, write `scale = 2f`
```

**Use the primitive variants where they apply.** `mutableIntStateOf`, `mutableFloatStateOf`, `mutableLongStateOf` and `mutableDoubleStateOf` avoid autoboxing every value. [AlbumViewerScreen.kt:140](../app/src/main/java/uno/lux/mosaic/album/ui/AlbumViewerScreen.kt#L140) uses `mutableFloatStateOf` for zoom scale — a value that changes every frame of a pinch — and plain `mutableStateOf` for the `Offset` beside it, because `Offset` is not a primitive.

**`mutableStateOf` alone is not enough inside a composable.** On its own it allocates a brand-new box on every recomposition, so the value resets constantly. It needs `remember`.

### `remember` — survives recomposition, and nothing else

`remember { }` stores a value in the composition, keyed by call-site position, and returns the same instance on every subsequent recomposition.

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val progress = remember { Animatable(0f) }
```

`remember` is orthogonal to `mutableStateOf`. `remember` gives *identity across recomposition*; `mutableStateOf` gives *observability*. You often want both, but `SnackbarHostState` and `Animatable` above need only the first, because each carries snapshot state internally.

**`remember` with keys** re-runs the calculation when a key changes:

```kotlin
val state = remember(holdMillis, hintMillis) { HoldToConfirmState(holdMillis, hintMillis) }
var display by remember(body, maxLines) { mutableStateOf(AnnotatedString(body)) }
```

The second line, from [PostContent.kt:196](../app/src/main/java/uno/lux/mosaic/post/ui/PostContent.kt#L196), is the pattern worth internalising: keying the `remember` on `body` means a row recycled to a *different* post discards the trimmed text computed for the old one. A `remember` with no key here would show the previous post's ellipsised body.

`remember` dies on a configuration change. That is the whole reason the next tool exists.

### `rememberSaveable` — survives configuration change and process death

`rememberSaveable` writes its value into the saved instance state, so it comes back after a rotation *and* after the system kills a backgrounded process.

```kotlin
var showSheet by rememberSaveable { mutableStateOf(false) }
var showReportDialog by rememberSaveable { mutableStateOf(false) }
var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
```

**This project's rule: any state a composable owns outright is `rememberSaveable`, not `remember`.** That explicitly includes whether a sheet or dialog is open — see [PostOverflowMenu.kt:76](../app/src/main/java/uno/lux/mosaic/post/ui/PostOverflowMenu.kt#L76). Rotating mid-report must not throw the report away.

`remember` stays correct for state that is meaningless after recreation: an `Animatable`, a transient gesture offset, a measured text layout.

`rememberSaveable` handles types the saved state understands out of the box. For anything else, supply a `Saver` (`listSaver`, `mapSaver`) or make the type `@Serializable`. If a value needs a hand-written `Saver` to survive, that is usually a sign it belongs in a ViewModel instead.

### `mutableStateListOf` / `mutableStateMapOf` — observable collections

`remember { mutableStateOf(emptyList<T>()) }` observes *reassignment* of the list. `mutableStateListOf<T>()` observes *mutation* of the list — `add`, `remove`, `set`.

Prefer plain `mutableStateOf` over an immutable list in this codebase. It composes with `copy()`, it is trivially hoistable into a ViewModel, and it keeps the "state is a value" model intact. Reach for `mutableStateListOf` only for a large, frequently-mutated, composition-local collection where allocating a new list per change is measurably wasteful.

### `derivedStateOf` — coalesce a noisy signal

```kotlin
val hasText by remember { derivedStateOf { textState.text.isNotBlank() } }
```

Every keystroke changes `textState.text`. Only the *first* and the *last* keystroke change `isNotBlank()`. `derivedStateOf` makes readers of `hasText` recompose only when the boolean flips — [PostDetailScreen.kt:610](../app/src/main/java/uno/lux/mosaic/post/ui/PostDetailScreen.kt#L610).

The rule: **use it when the input changes far more often than the output.** Scroll offset to "is the bar elevated". Text to "is the send button enabled". List state to "show the scroll-to-top button".

**Do not use it when the signal is already coalesced.** [HomeScreen.kt:154](../app/src/main/java/uno/lux/mosaic/feed/ui/HomeScreen.kt#L154) reads `listState.canScrollBackward` directly, with a comment saying why: it is already a boolean that only changes when the list crosses the top. Wrapping it would add an object and a subscription to buy nothing.

`derivedStateOf` must sit inside a `remember`. Without one you allocate a fresh derived state per recomposition and lose the caching entirely.

### `rememberUpdatedState` — read the latest value without restarting

This one solves a specific, common bug: a long-lived effect that must *see* the current value of a parameter without being *keyed* on it.

```kotlin
val currentPosts by rememberUpdatedState(posts)

LaunchedEffect(listState, playback, autoPlayVideos) {
    snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
        val url = videoToPlay(
            mostVisibleUrl = mostVisibleVideo(layoutInfo, currentPosts)?.videoUrl,
            activeUrl = playback.activeVideoUrl,
            autoPlayVideos = autoPlayVideos,
        )
        if (url != null) playback.playInline(url) else playback.stopPlayback()
    }
}
```

From [HomeScreen.kt:283](../app/src/main/java/uno/lux/mosaic/feed/ui/HomeScreen.kt#L283). Toggling a like replaces the `posts` list instance, but it changes neither which posts carry a video nor where they sit. Keying the collector on `posts` would tear it down and rebuild it on every heart tap. `rememberUpdatedState` lets the running collector read the newest list while the effect's keys stay stable.

Note the contrast inside the same effect: `autoPlayVideos` **is** a key, deliberately, because restarting the collector is exactly what makes flipping the setting on play the video already on screen.

The same tool keeps a gesture from being rebuilt when a callback's identity changes — [HoldToConfirmButton.kt:159](../app/src/main/java/uno/lux/mosaic/app/ui/components/HoldToConfirmButton.kt#L159) and [ClickDebounce.kt:35](../app/src/main/java/uno/lux/mosaic/app/util/ClickDebounce.kt#L35).

### `snapshotFlow` — snapshot state to `Flow`

`snapshotFlow { }` converts state reads into a cold `Flow` that emits on change, with `distinctUntilChanged` applied. It is the bridge out of Compose's snapshot world into the coroutine world, so you can `debounce`, `filter` or `collect` scroll position like any other stream. See [Pagination.kt:37](../app/src/main/java/uno/lux/mosaic/common/ui/Pagination.kt#L37) and [ImagePrefetch.kt:54](../app/src/main/java/uno/lux/mosaic/app/util/ImagePrefetch.kt#L54).

### `produceState` — suspend or `Flow` to state

The mirror image: launch a coroutine and push its results into a `State<T>`. Useful for a one-off async read local to a composable. This project uses it nowhere, on purpose — anything worth loading is worth loading in a ViewModel, where it survives rotation and is testable on the JVM.

### `collectAsStateWithLifecycle` — the ViewModel seam

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

**Always this, never `collectAsState`.** `collectAsState` keeps collecting while the app is in the background, so a backgrounded screen keeps working and keeps its upstream flows hot. `collectAsStateWithLifecycle` stops at `STOPPED` and resumes at `STARTED`.

Every stateful binder in this codebase looks like [HomeScreen.kt:121](../app/src/main/java/uno/lux/mosaic/feed/ui/HomeScreen.kt#L121).

### State holder classes — when logic outgrows a composable

When several pieces of state move together under rules, extract a plain class:

```kotlin
@Stable
internal class HoldToConfirmState(
    private val holdMillis: Long,
    private val hintMillis: Long,
) {
    var phase: HoldPhase by mutableStateOf(HoldPhase.IDLE)
        private set

    private var presses = 0

    suspend fun press(awaitRelease: suspend () -> Unit, onConfirm: () -> Unit) { ... }
}
```

Two things to notice in [HoldToConfirmButton.kt:70](../app/src/main/java/uno/lux/mosaic/app/ui/components/HoldToConfirmButton.kt#L70). First, `by mutableStateOf(...)` with **no `remember`** — the class is not a composable, and the instance itself is remembered at the call site. Second, `private set`: the state is observable to everyone and writable only by the state machine. This is the shape of every `rememberFooState()` in the Compose libraries.

The payoff is testability. The press, hold, hint and idle sequence is unit-testable with no frame clock and no composition.

### ViewModel `StateFlow` — screen state

Anything that outlives a configuration change, needs a coroutine scope, or talks to a repository lives in the ViewModel as a `StateFlow`. This is the default for screen state in this project. `remember` is for what the *composable itself* owns.

### `SavedStateHandle` — what the user typed

The last tier. The back stack is restored after process death; every ViewModel is rebuilt from scratch. A page that fetches can re-fetch — but nobody can re-derive a half-written post. See [SavedDraft.kt](../app/src/main/java/uno/lux/mosaic/app/util/SavedDraft.kt) and its use in [CreatePostViewModel.kt:47](../app/src/main/java/uno/lux/mosaic/composer/ui/CreatePostViewModel.kt#L47):

```kotlin
private val _uiState = MutableStateFlow(
    CreatePostUiState(form = savedStateHandle.restoreDraft(DRAFT_KEY) ?: CreatePostForm()),
)

init {
    savedStateHandle.saveDraft(DRAFT_KEY) { _uiState.value.form }
}
```

`saveDraft` registers *where to read the draft from* as a `() -> T`, so the platform pulls the value at most once per save and typing costs nothing.

### The survival table

This is the table to have in your head. "Survives" means the value is still there afterwards.

| Mechanism | Recomposition | Config change | Process death | Back-stack pop |
|---|:---:|:---:|:---:|:---:|
| Plain local `var` | ✗ | ✗ | ✗ | ✗ |
| `remember` | ✓ | ✗ | ✗ | ✗ |
| `rememberSaveable` | ✓ | ✓ | ✓ | ✗ |
| ViewModel `StateFlow` | ✓ | ✓ | ✗ | ✗ |
| ViewModel + `SavedStateHandle` | ✓ | ✓ | ✓ | ✗ |
| Repository (`@Singleton`) | ✓ | ✓ | ✗ | ✓ |
| Server / DataStore | ✓ | ✓ | ✓ | ✓ |

Two rows are worth saying out loud. A **ViewModel does not survive process death** — only `SavedStateHandle` does; the ViewModel is what survives *rotation*. And **nothing composition-local survives a pop**, which is the point: a popped page's state should be gone.

## 4. State hoisting and the stateful/stateless split

**Hoisting** means moving state up to the caller and passing `value` down plus `onValueChange` up. The composable becomes stateless: same inputs, same output, no memory.

Every screen in this project is split in two, and the split is worth stating as a rule:

```kotlin
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    HomeScreen(uiState = uiState, isRefreshing = isRefreshing, actions = viewModel, ...)
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    isRefreshing: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
)
```

The **binder** injects the ViewModel and collects. The **stateless** overload takes pure inputs and callbacks. Only the second is previewable, and only the second is testable without a ViewModel — which is why the project writes it this way even for a screen that will only ever have one caller.

**Hoist to the lowest common ancestor of every reader.** Hoisting further than that spreads recomposition wider than it needs to be, and turns a self-contained control into a component whose caller has to manage its internals.

## 5. Where should this state live — the ladder

Ask in order, and stop at the first yes:

1. **Does anything outside this composable read or write it?** Hoist it to the caller.
2. **Must it survive a configuration change, or does changing it need a coroutine or a repository?** ViewModel `StateFlow`.
3. **Is it something the user typed that nobody could re-derive?** ViewModel plus `SavedStateHandle`.
4. **Does it matter after the process is killed, but is otherwise UI-only?** (a dialog being open) `rememberSaveable`.
5. **Is it meaningless after recreation?** (an `Animatable`, a gesture offset, a text measurement) `remember`.

The common mistake is starting at 5 and never revisiting. The opposite mistake is putting an `Animatable` in a ViewModel because "state belongs in the ViewModel".

## 6. What actually triggers a recomposition

Exactly one thing: **a composable read a snapshot state value, and that value changed.**

Not "the parent recomposed". A parent recomposing gives a child the *opportunity* to recompose, but the child **skips** when all its parameters are equal to last time and it has no invalidated state read of its own.

Skipping requires parameter comparison, which used to require *stability*. With **strong skipping** — on by default in Kotlin 2.x, and on in this project — the rules are looser:

- Composables with **unstable** parameters can still skip, compared by **instance equality**.
- **Lambdas are auto-remembered**, so you no longer wrap callbacks in `remember` to keep a child skippable.

The practical consequence, stated in `AGENTS.md`: **do not wrap lambdas in `remember` by reflex, and do not chase `@Stable`/`@Immutable` annotations.** Feed rows skip fine even though `Post` carries an `Instant` and a `List`. Section 7 covers the cases where those annotations still earn their keep.

What strong skipping does *not* fix is identity churn. Sections 7 and 8 are about that.

## 7. Stability — `@Stable` and `@Immutable`

**Stability is the compiler's answer to one question: "if I skip this composable, can the UI go stale?"**

A type is **stable** when three things hold:

1. `equals` is consistent — two instances that compare equal always produce the same UI.
2. If a public property changes, composition is notified.
3. Every public property is itself of a stable type.

The compiler infers this where it can. Primitives, `String`, enums, function types and lambdas are stable. A `data class` whose properties are all `val`s of stable types is stable. Two big categories are **not**:

- **Collection interfaces.** `List`, `Set` and `Map` are unstable, because the declared type says nothing about whether the implementation is mutable. `listOf(...)` returns something immutable in practice, and the compiler cannot know that. `kotlinx.collections.immutable`'s `ImmutableList` is stable, because the type itself carries the guarantee.
- **Types from a module the Compose compiler did not compile.** `java.time.Instant`, most library models, anything from a pure-Kotlin module without the plugin. The compiler has no inference to run, so it assumes the worst.

That is why `Post` is unstable here: it carries an `Instant` and a `List`.

### What the annotations promise

```kotlin
@Immutable          // public properties will never change after construction
data class Palette(val swatches: List<Color>)

@Stable             // properties may change, and every change notifies composition
internal class HoldToConfirmState(...)
```

`@Immutable` is the stronger claim: nothing observable changes, ever. `@Stable` allows mutation, on the condition that every mutation goes through snapshot state so readers are invalidated. A class holding `by mutableStateOf(...)` satisfies that, which is exactly the case for [HoldToConfirmButton.kt:70](../app/src/main/java/uno/lux/mosaic/app/ui/components/HoldToConfirmButton.kt#L70).

Both are also usable on a function or a property, where they mean "the same input always yields the same result".

**Both are promises the compiler cannot verify, and a wrong one is a silent bug.** Annotate a type whose properties change without notifying composition, and Compose will skip a composable that should have re-run. The screen shows stale data, nothing crashes, and no warning appears. This is the worst failure mode in Compose — treat these annotations as an assertion you are certifying, not as a performance dial.

### Does any of this still matter with strong skipping?

Less than it did, and the difference is precise. Under strong skipping the comparison used to decide skipping depends on stability:

| Parameter | Compared with |
|---|---|
| Stable | `equals` |
| Unstable | instance equality (`===`) |

So an unstable parameter that is a **new instance but equal in value** does not skip. `posts.filter { ... }` recomputed in a recomposition produces exactly that — an equal list, a fresh instance, a child that recomposes for nothing. On a stable type it would have skipped.

Three places where declaring stability still earns its keep:

1. **A value type whose equal-but-new instances are common** — a mapped list, a formatted value object, anything rebuilt per emission. `@Immutable` buys `equals` comparison.
2. **A mutable state-holder class** you pass into composables. `@Stable` states the notification contract and is documentation as much as a compiler hint.
3. **Third-party types you cannot annotate.** Use a **stability configuration file** (`stabilityConfigurationFile` in the Compose compiler options) to list classes such as `java.time.Instant` as stable, rather than wrapping them.

### Where this project uses them

Three places, and no others. Domain models such as `Post` are left unannotated on purpose — `AGENTS.md` states the rule outright: do not chase `@Stable`/`@Immutable`. Feed rows skip fine as they are, because `PostRepository` preserves instance identity for every unchanged post, which is the subject of the next section and worth far more than any annotation.

**1. The actions interfaces.** Every stateless screen takes its callbacks bundled into one interface, and each is `@Stable` — [HomeScreen.kt:75](../app/src/main/java/uno/lux/mosaic/feed/ui/HomeScreen.kt#L75), and the same in `ProfileScreen`, `CreatePostScreen` and `EditProfileScreen`:

```kotlin
@Stable
interface HomeActions {
    fun refresh()
    fun onToggleLike(postId: PostId)
    ...
}
```

The ViewModel implements it, so the binder passes the ViewModel straight through — a `@Singleton`-lifetime object whose identity never changes, and whose every observable field is snapshot state or a `StateFlow`. The annotation is honest, and it collapses what would otherwise be a dozen separate callback parameters into one stable one.

**2. Snapshot-state holders.** `@Stable class VideoPlaybackController` ([VideoPlayback.kt:50](../app/src/main/java/uno/lux/mosaic/video/ui/VideoPlayback.kt#L50)) and `@Stable internal class HoldToConfirmState`. Both mutate, and both mutate exclusively through `by mutableStateOf(...)`, which is precisely the contract `@Stable` names.

**3. A theme token bag.** `@Immutable data class MosaicColors` ([MosaicColors.kt:12](../app/src/main/java/uno/lux/mosaic/app/theme/MosaicColors.kt#L12)) — two `val Color`s, constructed once per theme and never touched again.

### The annotation is taken at its word — a real bug from this repo

`@Stable` says "compare me with `equals`", and the compiler emits exactly that call. [ActionsInvocationHandler.kt](../app/src/main/java/uno/lux/mosaic/app/util/ActionsInvocationHandler.kt) exists because of it. Previews pass a `java.lang.reflect.Proxy` in place of an actions interface, and the naive handler returned `Unit` from every method — including the `equals` that recomposition calls on a stable parameter:

```
result has type boolean, got kotlin.Unit
```

The fix is to answer `Object`'s three methods honestly instead of swallowing them:

```kotlin
when (method.name) {
    "equals" -> proxy === args?.firstOrNull()
    "hashCode" -> System.identityHashCode(proxy)
    else -> "ActionsProxy@${Integer.toHexString(System.identityHashCode(proxy))}"
}
```

Worth remembering as the concrete form of "the compiler cannot verify these annotations". Here it surfaced as a crash. The quieter version — a type that mutates without notifying composition — surfaces as a screen that simply stops updating.

### The order to work in

**Measure first** — recomposition counts in Layout Inspector, or `--profile`. Find what is actually recomposing, and only then decide whether the fix is identity preservation, a smaller read scope, or a stability annotation. In that order, because the first two are almost always the real answer.

## 8. Preventing recomposition — preserve identity

Because skipping compares by instance, **a mutation must leave every unchanged thing instance-identical.**

The canonical example is `PostRepository`, which stores a `Map<PostId, Post>` and updates one entry:

```kotlin
_entities.update { it + (postId to updated) }
```

Every other `Post` is the same instance, so a like recomposes exactly one `PostCard`. Written as `map { it.copy() }` — which is what a read-modify-write of the whole collection amounts to — the entire visible feed would recompose.

Two more identity rules:

- **Key your lazy list items.** `items(posts, key = { it.id })` lets Compose match items across changes instead of composing by position. Without it, prepending one post recomposes the whole visible window.
- **Do not build a new collection per recomposition where a child compares it.** Strong skipping auto-remembers lambdas, but not `posts.filter { ... }`. Hoist a derived list into `remember(posts) { ... }`, or into the ViewModel.

## 9. Scoping state reads — read it as low and as late as you can

Two techniques, both about shrinking the invalidated scope.

**Read state in the smallest composable that uses it.** Reading `pagerState.currentPage` at the top of the album viewer would invalidate the whole screen — pager, images, chrome — on every swipe. Instead the read lives inside a separate `PageIndicator` ([AlbumViewerScreen.kt:111](../app/src/main/java/uno/lux/mosaic/album/ui/AlbumViewerScreen.kt#L111)):

```kotlin
@Composable
private fun PageIndicator(pagerState: PagerState, total: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier...) {
        Text(text = "${pagerState.currentPage + 1} / $total", ...)
    }
}
```

Swiping now recomposes a pill.

**Defer the read into a lambda.** Passing `alpha: Float` forces the caller to recompose whenever alpha changes. Passing `alpha: () -> Float` and invoking it inside `graphicsLayer` moves the read into the layer phase ([HoldToConfirmButton.kt:255](../app/src/main/java/uno/lux/mosaic/app/ui/components/HoldToConfirmButton.kt#L255)):

```kotlin
@Composable
private fun HoldLabel(text: String, color: Color, alpha: () -> Float) {
    Text(
        text = text,
        modifier = Modifier
            .clearAndSetSemantics {}
            .graphicsLayer { this.alpha = alpha() },
    )
}
```

The same idea covers `Modifier.offset { IntOffset(...) }` over `Modifier.offset(x.dp)`, and `drawBehind { }` over a recomposed `Canvas`. A rule of thumb: **a short transition of a small subtree may animate in composition; anything larger, or anything per-frame, belongs in the layer or draw phase.**

## 10. Effects — running non-UI work correctly

A composable must be free of side effects, because it can run at any time, on any thread, and be abandoned. Side effects go in effect APIs, and every one of them is keyed.

| API | Runs when | Use for |
|---|---|---|
| `LaunchedEffect(keys)` | Enters composition, restarts on key change | Suspend work tied to a screen — animation, a snackbar, a collector |
| `DisposableEffect(keys)` | Same, plus an `onDispose` block | Anything with a matching teardown — listeners, a player, window flags |
| `SideEffect` | After every successful recomposition | Publishing Compose state to a non-Compose object |
| `rememberCoroutineScope()` | Returns a scope tied to the call site | Launching from a **callback**, not from composition |

**The keys are the API.** `LaunchedEffect(Unit)` never restarts, which is right for a one-shot and wrong for anything that should follow a value. `LaunchedEffect(refreshErrorMessage)` in [HomeScreen.kt:165](../app/src/main/java/uno/lux/mosaic/feed/ui/HomeScreen.kt#L165) shows a new snackbar per new message, and the message is cleared once shown so a rotation cannot replay it.

The matching trap is over-keying: keying on a value you only *read* inside the effect restarts it needlessly. That is the `rememberUpdatedState` case from section 3.

`DisposableEffect(darkTheme)` in [MainActivity.kt:57](../app/src/main/java/uno/lux/mosaic/app/ui/MainActivity.kt#L57) re-applies edge-to-edge bar styling on a theme change; `DisposableEffect(lifecycleOwner, playback)` in [VideoPlayback.kt:174](../app/src/main/java/uno/lux/mosaic/video/ui/VideoPlayback.kt#L174) tears the player down.

## 11. Bugs this document exists to prevent

1. **`mutableStateOf` without `remember`.** The value resets on every recomposition. The IDE warns; believe it.
2. **`remember` where `rememberSaveable` belongs.** Everything looks correct until a rotation closes the user's half-filled dialog.
3. **`derivedStateOf` without `remember`.** Allocates per recomposition and caches nothing — strictly worse than the plain read.
4. **Keying an effect on data you only read inside it.** The effect restarts constantly and throws away the work it had done.
5. **Rewriting a whole collection to change one item.** Everything visible recomposes and, in a repository, a concurrent update gets clobbered by the stale snapshot read before the suspension point.

## 12. Rapid-fire

- **`remember` vs `rememberSaveable`?** Recomposition, versus configuration change *and* process death. The second needs a value the saved state can store, or a `Saver`.
- **Why does my `LaunchedEffect` run twice?** A key changed. Usually an unstable lambda or a fresh collection identity in the key list.
- **`collectAsState` or `collectAsStateWithLifecycle`?** Always the lifecycle-aware one; the other collects in the background.
- **Does the whole screen recompose when state changes?** No — only scopes that read that state, and children skip on equal parameters.
- **Is `@Immutable` still needed?** Rarely, under strong skipping — see section 7. It buys `equals` comparison instead of instance comparison; preserving identity across mutations matters far more.
- **Where does scroll position go?** `rememberLazyListState()` — it is already saveable internally.
- **Why is hoisting worth the extra parameters?** It is what makes a composable previewable and testable without a ViewModel, which is the whole reason for the project's two-function screen split.

## See also

- [Coroutines.md](Coroutines.md) — `StateFlow`, `stateIn` and the structured concurrency behind every ViewModel here
- [Dependency Injection.md](Dependency%20Injection.md) — how a screen's ViewModel is built, including per-page runtime arguments
- `AGENTS.md`, sections *Compose performance* and *Surviving process death* — the project rules this document explains
