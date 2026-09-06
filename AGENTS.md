# AGENTS.md

Guidance for AI agents working in this repository.

## Project purpose

This is a sample Android app (`uno.lux.sample`, shown as "Sample"). It is a resume and portfolio piece. It shows modern Android architecture.

Code quality, idiomatic Compose, and clear architecture are the goal. Working features alone are not enough. Always prefer the current recommended Android method over a quick shortcut.

## Testing philosophy — tests first

Tests are the **primary consumer** of this codebase. Users come second.

- **Write the test first.** A change is not done until tests cover it and the tests pass. New logic must land with its test in the same change.
- **Design for the test.** Logic lives in plain-JVM units, with constructor injection and no Android dependencies. Pure functions take their inputs (for example `now`) as parameters instead of reading ambient state. This is *why* the architecture has this shape: it makes the code testable.
- **Same rule for UI.** Stateless composables take data and callbacks as parameters. ViewModels expose a `StateFlow` that a test can assert against.

Shared test infrastructure lives in `app/src/test/java/uno/lux/sample/testing/`. It includes `MainDispatcherRule` (swaps `Dispatchers.Main`), `ViewModelTest` (a base class for ViewModel tests), `BackStacks` (`backStackOf`/`screens`, for driving the `Navigator`), and `TestUtils`.

Fakes are hand-written. Each fake lives beside the thing it stands in for, for example `post/data/FakePostDataSource.kt`. The project uses no mocking framework.

## Commands

The shell is Windows PowerShell. Invoke the wrapper as `.\gradlew.bat`.

**Every variant task name carries a `server` flavor** (see *Which server a build talks to*). `remote` is the default flavor, and the one every command below names.

- Unit tests: `.\gradlew.bat testRemoteDebugUnitTest`. For one class, add `--tests "uno.lux.sample.post.data.PostRepositoryTest"`. Add `.<method>` to run one test.
- Lint: `.\gradlew.bat lintRemoteDebug`. The report is at `app/build/reports/lint-results-remoteDebug.html`. For formatting, use `.\gradlew.bat ktlintCheck`.
- Build or install: `.\gradlew.bat assembleRemoteDebug` or `.\gradlew.bat installRemoteDebug`.
- Instrumented tests (need a device): `.\gradlew.bat connectedRemoteDebugAndroidTest`. For one class, add `-Pandroid.testInstrumentationRunnerArguments.class=…`.

**CI** (`.github/workflows/ci.yml`) runs `ktlintCheck`, `lintRemoteDebug`, and `testRemoteDebugUnitTest` on every push to main and every pull request. It uploads the reports as artifacts. These three JVM-only checks are the checks that gate a change.

**There is no emulator job.** No automatic process runs the instrumented suite. A change to process-death or back-stack behavior needs the user to run it.

**Do not touch a real device or emulator unless the user asks.** This rule covers `connectedRemoteDebugAndroidTest`, `installRemoteDebug`, `adb`, and driving the app by hand.

Run the three JVM-only checks instead. State plainly which parts of a change they cover and which parts need a device. Write the instrumented test when the behavior needs one. Leave running it to the user.

## Toolchain / build setup

- **Kotlin 2.4.10**, **AGP 9.3.1**, **Gradle 9.6.1**, Compose BOM **2026.06.01**. Java 11 is the source and target version. The Gradle daemon runs on JDK 21.
- `compileSdk` and `targetSdk` are both **37** (the `release(37)` DSL). `minSdk` is **26**. The project bumps these two settings separately, on purpose. A new SDK version lands on `compileSdk` first. It moves to `targetSdk` only after a review of its behavior changes. That review is cheap here for five reasons: the app declares only the `INTERNET` permission, it picks media through `PickVisualMedia`, it runs no foreground service, it is already edge-to-edge, and it ships no native code. Check these five items again when the next SDK version lands.
- The project uses AGP 9's **built-in Kotlin**, so it has no `org.jetbrains.kotlin.android` plugin. Annotation processing uses **KSP**, because kapt is not compatible. Hilt runs on KSP too.
- AGP bundles Kotlin 2.2.10. The root `build.gradle.kts` raises the compiler version to 2.4.10 through the **buildscript classpath**. This is the documented way to override it. The Compose compiler, kotlinx-serialization, and **Mappie** are locked to the Kotlin version. **A Kotlin version bump is blocked until Mappie publishes a matching build.** KSP versions on its own schedule (`2.3.10`).
- Add dependencies to the **version catalog** (`gradle/libs.versions.toml`) and reference them through `libs.*`. Never hardcode a version number in a build script.

## Backend

The backend is a **Ruby on Rails** app. `NetworkModule` holds the host as `BASE_URL` and builds `API_URL` from it.

### Which server a build talks to

`BASE_URL` is a `buildConfigField`, set by the **`server` flavor dimension** in `app/build.gradle.kts`. There is no in-app switch, and both flavors keep the one `applicationId`, so swapping servers is the Build Variants dropdown — or a `Remote`/`Local` task name — and never a reinstall under a different package.

- **`remote`** (`isDefault`) — the deployed host, `https://mosaic.tree-among-shrubs.com`.
- **`local`** — `http://localhost:3000`, reached through an adb reverse tunnel.

**The tunnel opens itself.** `adbReverseLocalServer` in `app/build.gradle.kts` runs `adb reverse tcp:3000 tcp:3000` for every attached device, and it finalizes every `assembleLocal*` and `installLocal*` task. It hangs off *assemble* rather than install because Android Studio's Run button deploys the APK itself and never calls the install task.

The tunnel forwards the device's own port 3000 to the dev machine's, over adb — the route that works on the emulator and over USB alike, with no firewall rule, no admin, and nothing to change when the machine's IP moves. It dies with the emulator and with the adb server, which is why the task is never up to date and simply re-runs.

**The task never fails the build.** No device attached, no adb on the machine, an adb that errors — each is one line on the console and nothing more, because assembling an APK without a device is ordinary. It also wires up nothing at all when the host below is overridden away from loopback, since a device reaching the server over the network needs no tunnel.

Two settings override the host and port, each read from an environment variable first, then a Gradle property (`~/.gradle/gradle.properties`, or `-P` on the command line):

| Setting | Environment variable | Gradle property | Default |
| --- | --- | --- | --- |
| Host | `MOSAIC_LOCAL_HOST` | `mosaic.localHost` | `localhost` |
| Port | `MOSAIC_LOCAL_PORT` | `mosaic.localPort` | `3000` |

Both are read through `providers`, so they stay compatible with the configuration cache.

**Cleartext HTTP is scoped to the `local` flavor**, through `app/src/local/`'s manifest and network security config. The address is a build-time setting, so the exemption cannot name a domain — which is why it is a `base-config` on a flavor that only ever points at a dev machine. Every `remote` build, release included, keeps cleartext blocked.

**Why the tunnel rather than an IP.** The server runs under WSL, and WSL's mirrored networking (`networkingMode=mirrored`) relays a Linux listener to Windows *processes* only — it opens no Windows listening socket. So `10.0.2.2:3000`, the emulator's alias for the host machine, is refused, and the dev machine's LAN address times out, even with an inbound Hyper-V firewall rule allowing the port. Both were measured: a Windows-native listener on another port answered the emulator on both addresses at the same moment Rails on 3000 did not. adb sidesteps the whole question.

Rails still has to listen broadly: `bin/rails server -b 0.0.0.0 -p 3000`.

Reaching the server from a device over **Wi-Fi** rather than USB needs a real Windows listener in front of it — `netsh interface portproxy add v4tov4 listenport=3000 listenaddress=0.0.0.0 connectport=3000 connectaddress=127.0.0.1`, plus an inbound Windows firewall rule — and then `MOSAIC_LOCAL_HOST` set to the machine's LAN address.

**There is no sign-in.** The app seeds the signed-in user from `app/fixtures/SampleData.kt` and sends it as an `X-User-Id` header. The server uses this header to scope viewer state (`isLiked`, `isBookmarked`) and to enforce ownership. Deleting someone else's post and reading someone else's bookmarks both return a 403 from the server, not only from the client.

**Client limits mirror server validations**: `CreatePostMaxImages`, `CreatePostMaxVideoBytes`, composer field lengths, `REPORT_DETAILS_MAX_LENGTH`, and the `ReportReason` list. Changing one side means changing the other side too.

Reporting a post goes nowhere. `POST /api/posts/:id/report` validates the request, then logs and discards it, and answers with 204. There is no moderation queue. Because of this, the UI must never let a user type a report the server would refuse — a 422 is the one thing the reporter cannot fix.

**The report dialog waits for that 204 anyway.** It stays up for the whole request with Send disabled, closes on the answer, and only then shows the thanks; a failure is stated in the dialog, which is still on screen with the reason and details the user picked. The states are `ReportSendState` in `post/ui/ReportSend.kt`, and `launchReport` / `dropReport` are the shapes the three reporting ViewModels drive them with. `dropReport` cancels the request as the dialog closes, so an answer nobody is waiting for cannot settle into the *next* dialog. A report is therefore not a `FailedAction`: that enum is for a tap whose UI is gone by the time the server answers, and this one's is not.

The reason's *wire* spelling lives in `post/data/network/ReportPostRequestDto.kt`. This keeps the domain enum free of wire concerns. Blank details are left out of the request body rather than sent as an empty string.

## Package structure — where a new file goes

The top level of the package tree is **slices, not layers**. Layers are the shape *inside* a slice. There is no root `data/` or `ui/` package, and, on purpose, no `activities/`, `viewmodels/`, or `dialogs/` package. **A file's location depends on what it is about, never on what class it extends.**

```
post/ user/ comment/ album/ video/           aggregates: the entity, its wire types, its store, its UI
feed/ profile/ composer/ settings/ shell/    features and read models — they own no entity

common/              what two or more concerns share — ui/ noun-free composables, data/ wire types and files
app/                 the machine — MainActivity, MosaicApplication, MosaicApp
app/di/              Hilt modules
app/navigation/      Navigator, Screen keys, BackStackEntry, page transitions
app/theme/           the Mosaic palette, type and gradients
app/ui/components/   the Mosaic design system — branded controls, no domain noun
app/util/            pure functions with zero project imports, plus composables that draw nothing
app/fixtures/        stand-in content for previews and DI seeding
```

Inside a concern there are four layers and no fifth:

```
<concern>/data/            the repository and its DataSource interface
<concern>/data/domain/     the models — pure Kotlin, no platform and no wire
<concern>/data/network/    the service, DTOs, mappers — the only place HTTP appears
<concern>/ui/              composables and ViewModels
```

**`app/` versus `common/`**: both hold noun-free composables. The difference is whose vocabulary the composable belongs to. `app/ui/components/` is the branded design system, for example `HoldToConfirmButton`. `common/ui/` is noun-free UI that **two or more** concerns actually need, for example `FullScreenError` and `DiscardChangesDialog`. Being noun-free is not enough to earn a place in `common/`. A generic composable with one consumer belongs to that consumer's slice. Move it to `common/` only on the day a second consumer needs it.

**Aggregates versus read models** keep the dependency graph directed. `post`, `user`, `comment`, `album`, and `video` each own an entity. `feed` and `profile` own no entity. They hold ordered *IDs* and paging state, and resolve the IDs through the entity stores. This is why `ProfileRepository` *composes* `PostRepository`.

> Features may depend on aggregates. **Aggregates never depend on features.**

```
user  album  video  settings  -> (nothing)
post      -> album comment user video      comment  -> post user
feed      -> post settings user video      profile  -> post user video
composer  -> feed post                     shell    -> feed profile user
```

Every slice may depend on `app`. `app` wires everything together, except for `app/theme`, `app/util`, and `app/ui/components`, which import no slice.

The feature-to-feature edges are deliberate. `composer -> feed`: publishing a post prepends the new ID to the feed. `feed -> settings`: auto-play reads a setting. `shell -> feed profile`: its tabs *are* those screens.

`post <-> comment` is a real cycle, and the project tolerates it. If it ever causes a problem, the fix is to fold `comment/` into `post/`.

**To file a new file, ask in order:**

1. **Does it mention a domain noun?** No → 2. Yes → 3.
2. Pixels → branded control → `app/ui/components`; color/font → `app/theme`; needed by 2+ concerns → `common/ui`; one concern → that concern. Draws nothing → `app/util`, even when `@Composable`. Wires the app together → `app`. Neither → `common/data`.
3. Claimed by exactly one feature → that feature. By several → the noun's aggregate.

The moment a "helper" imports `Post`, it is post code. File it in `post/`.

**Two things look like violations but are not.** First, a cross-slice KDoc reference is written fully qualified, for example `[uno.lux.sample.profile.data.ProfileRepository]`, because a `[Link]` needs an import. Second, a wire type shared by two features belongs to the aggregate it describes, for example `SideloadedUsers` belongs in `user/data/network/`. When it describes no single aggregate, it belongs in `common/data/network/`, for example `LikeToggleDto`.

**The `DataSource` interface stays beside its consumer, not its implementation.** This lets a repository compose a network source and a local source without either one owning the contract. It is also why `data/network/` nests inside the slice.

**Use one Retrofit service per slice**: `FeedApi`, `PostApi`, `CommentApi`, `UserApi`, `ProfileApi`. All come from the single `Retrofit` instance in `app/di/NetworkModule.kt`.

Do not bring back an app-wide API interface. The previous one fit in no slice. Its 338-line fake had to be implemented in full by every test that needed only one endpoint.

### ArchitectureTest

`app/src/test/java/uno/lux/sample/architecture/ArchitectureTest.kt` (Konsist) checks these rules against the real source tree. It fails the build on a violation. **Every rule comes from the file path, never from a list of names.** The test finds concerns as the top-level packages other than `app` and `common`, so adding a slice needs no edit to the test.

1. **Every concern package follows the convention** of the four layers. The other rules select files by these layer suffixes. This rule guards against a rule silently matching nothing.
2. **The wire stays in `data/network`.** Retrofit and OkHttp appear nowhere else, except `app/di`, for the one `Retrofit` instance.
3. **The domain layer is pure.** `data/domain` imports no platform code, no wire type, and no screen. Mappers convert only from DTO to model.
4. **`data` never depends on `ui`.**
5. **`common` knows no concern.**
6. **`app/theme`, `app/util`, and `app/ui/components` know no concern.** The rest of `app` is exempt from this rule, because wiring the app is its job.
7. **A repository with no interface stays plain-JVM.** A `*Repository` class with no supertype carries no Android import. `DataStoreSettingsRepository` and `AppCompatLocaleRepository` may touch the platform, because their consumers can be handed a test double instead.

The test does **not** check the direction of the cross-concern graph, on purpose. Encoding which cycles are tolerated would cost more than the code review that catches them.

Adding a rule means adding a test. Verify a new rule by planting a violation and watching the test fail. If a violation is *intended*, widen the rule and explain why in its failure message, instead of deleting the rule.

## One module, on purpose

**The app is a single `:app` module. It stays one module until a stated trigger fires.** Do not start a module split by accident. The slices map onto module boundaries almost mechanically, so a split stays possible later.

At about 150 files, a split would bring three benefits. `internal` would start to mean "private to the slice". This is the real prize, but the least urgent one. The compiler would enforce the cross-slice graph. This is the strongest argument for a split. Build avoidance would likely turn out *negative*, because KSP and Hilt codegen and configuration dominate build time here, not the recompiling of unrelated Kotlin.

A split would also cost the exhaustive `when` over the sealed `Screen`. Feature modules cannot see each other's screens, which would push navigation toward runtime route registration.

**Revisit this decision when** one of three things happens: boundaries are violated in practice and code review does not catch it, build times become a real complaint as measured with `--profile`, or a second contributor joins the project.

## Architecture

The app is single-activity and 100% Compose. It uses no Fragments and no XML layouts. XML under `res/` holds resources only.

### Entry point

`MainActivity` (`app/ui/MainActivity.kt`) is the only Activity, and it stays a thin shell. It collects `ThemeMode` from `MainViewModel`, resolves dark or light mode, applies the result to `MosaicTheme`, and hosts `MosaicApp()`.

It extends `AppCompatActivity` for **one reason only**: AppCompat's delegate applies the per-app language on devices below Android 13 (see *Localization*). The app uses no AppCompat UI.

### Navigation — Navigation 3, driven by ViewModels

`MosaicApp()` owns a `rememberBackStack` of `@Serializable` `BackStackEntry` keys. Each key pairs a `Screen` with the identity its state is scoped to. `Screen.Shell` is the permanent root. `Screen.Profile(userId)` and `Screen.Settings` push over it.

`NavDisplay` renders the top entry, using the push and pop specs in `app/navigation/PageTransitions.kt`, and it includes predictive back. Back handling is `onBack` popping the stack. **The code uses no hand-rolled `BackHandler`.** Entry decorators give each entry its own saveable state and `ViewModelStore`, so a pushed page's ViewModel is created on push and cleared on pop.

**Identity lives on the entry, not on the `Screen`.** Nav3 scopes everything per entry by `contentKey`, a function of the back-stack key *alone*.

With a bare `Screen` as the key, two pushes of `Screen.PostDetail("p1")` collapsed onto one scope. The second page inherited the first page's ViewModel and its half-typed comment. `BackStackEntry(screen, id)` fixes this. `Navigator.entryFor` is the only place that mints an id, so a new `Screen` cannot skip this step by accident.

`Navigator` takes its id source as a constructor parameter, `nextId`, which defaults to a UUID. **It must not use a counter.** The `@ActivityRetainedScoped` instance restarts on process death, while the restored stack still holds the old ids.

**A screen opts back into sharing identity through `Screen.sharedId`.** It defaults to `null`, which gives a fresh identity on every push. A constant value makes the screen one page wherever it opens. Deriving the value from an argument, for example `"post-$postId"`, shares identity per argument. Use this only when a page genuinely *is* one thing per argument.

`Screen.Shell` is the only screen that pins a shared id today. Declare `sharedId` as `get() = …`, never as an initialized property, because a backing field would be pulled into the serialized key.

**Navigation intent flows through ViewModels, not through host lambdas.** Every screen has a ViewModel that injects the `Navigator` (`@ActivityRetainedScoped` in `app/di/NavigationModule.kt`) and calls `goTo` or `goBack`.

Because of this, `MosaicApp` wires **no navigation lambdas at all**. It hands `NavDisplay` the shared `backStackEntryProvider`, where `contentKey = { it.id }` is stated once. A private `ScreenContent` function picks the page with one exhaustive `when` over the sealed `Screen`, so an unhandled page is a compile error.

The project does not use the `entryProvider { entry<T>() }` DSL, on purpose. It dispatches through a `KClass`-keyed map, which is pointless with only one key type, and its metadata cache is never pruned, so it would retain every entry ever pushed.

There are two push variants. `goTo` allows pushing a screen equal to the current top screen. This supports an intentional re-open, and the 500 ms click debounce on navigation controls covers accidental double taps. `goToSingleTop` is a no-op when its screen is already on top. Use it for pages that must never stack on themselves, for example Settings and the profile editor.

Unit tests drive the real `Navigator` through `testing/BackStacks.kt`'s `backStackOf(…)` and assert on `.screens()`. This keeps assertions about pages, not about identities.

### The shell

`shell/` is a feature, not part of the machine. It owns no entity, and it hosts other features' screens. `ShellScreen` uses `DividedNavigationSuiteScaffold`, Material 3's `NavigationSuiteScaffold`, so navigation adapts to window size with no per-form-factor code.

Destinations are data-driven from the `ShellDestinations` enum in `shell/ui/`. Each entry has a `@StringRes` label, an icon, and an optional `screen`. It lives in `ui/` because a label resource and a drawable are presentation. Add or change a tab by editing the enum.

- **Tab selection is plain `rememberSaveable`, not back-stack entries.** Switching tabs is not a navigation event, so the system back button never walks through tabs.
- **CREATE is deliberately not a tab.** A destination that carries a `screen` is an *action*. Selecting it pushes that page over the whole shell with `goToSingleTop`, instead of swapping the content area, so the tab underneath stays highlighted. This is why the `CREATE -> Unit` branch in the content `when` is unreachable.
- Each screen owns its own `Scaffold` and `TopAppBar`. Tab switches cross-fade. Page pushes slide.

### Data layer

`PostRepository` wraps a `PostDataSource` and holds a `StateFlow` entity cache. Because of this, like, bookmark, and delete mutations show up on every screen with no re-fetch. `FeedRepository` and `ProfileRepository` hold only ordered IDs, and resolve them through that store, so a deleted post vanishes everywhere in one emission. The ordered lists themselves are left untouched, on purpose.

- **Deletion** is offered only on the user's own posts. The client gates this on author ID, and the server enforces it too (`DELETE /api/posts/:id` returns 403 otherwise). Deletion cascades to comments, likes, and bookmarks, and to an `Album` or `Video` once no post holds it.
- **Comment counts** ride the same propagation, even though `CommentRepository` is stateless. `commentCount` is a field on the *post*, so `PostRepository.commentAdded` bumps the entity, but only **after** the server answers. On the server, the count is derived, with no column and one grouped query per page, so the next read replaces the bump instead of adding to it.
- **A like is not restricted to other people's posts.** Neither surface gates the heart by ownership. `isOwn` gates *deletion* only.
- **Like and bookmark are optimistic**, unlike `commentAdded` above. The heart is the most-tapped control in the app. It fills on the tap and reconciles when the server answers. On failure, the code restores the fields and rethrows. Keeping exactly one copy of each post to correct is what makes this safe.
  - **Every write re-reads the entity and touches only the fields it owns** (`PostRepository.updateEntity`). Reading the post once and writing that snapshot back later was a real bug. A refresh that landed mid-flight had its fresher post overwritten by a stale one. Avoid this shape: a repository mutation that writes back a whole entity read before a suspension point.
  - **The code drops an answer that a newer tap has moved past**, through `updateEntity`'s `stillOurs` predicate. So two taps in flight settle on the second tap, which is what the user asked for last.
  - **The endpoints are idempotent.** `PUT /posts/:id/like` and `PUT /posts/:id/bookmark` take a body, `{"liked": true}` or `{"bookmarked": true}`, instead of flipping whatever state they find. So a retry after a timeout cannot move the like twice. The old endpoints were bodiless `POST …/toggle` calls, and those could move the like twice. Comment likes share the same contract, `PUT …/comments/:id/like`. `PostApiLikeTest` pins the verb, path, and body against MockWebServer. Following a user is still a toggle.
  - **A like response returns `LikeState`, not the thing that was liked** (`common/data/`, mirroring `LikeStateDto`). Posts and comments both use it. Returning a whole entity is what let a stale copy get written back. Two fields cannot cause that bug.
- **A thread is paged like every other list.** Its paging window belongs to `PostDetailViewModel`, not to the stateless `CommentRepository`, because a cursor kept in the repository would outlive the list it points into. The ViewModel holds the comments, the cursor, the end flag, and the load error as **one** `CommentThread` value, so a page landing updates all of them together, and the screen can never see a thread grown past a cursor that has not. `CommentThread` sits *beside* `PostDetailUiState.Content` rather than inside its `Loaded` case, because the post and the first page are fetched in parallel on a cold start and whichever lands first needs somewhere to go. **The cursor is the whole guard**: it is null before the first page lands, and null again once the server says that page was the last, so `loadMoreComments` needs no flag of its own to know there is nothing left to ask for. A page whose cursor no longer matches the thread's cursor is *dropped*, not appended, because a reload that lands mid-flight has restarted the thread, and gluing the two together would mix comments from two different reads and create duplicate keys in the `LazyColumn`. The header always counts `post.commentCount`, never the loaded window.
  - **Comment likes are optimistic too, and the code deliberately does not share logic** with `PostRepository`. A comment's like lives in the list that `PostDetailViewModel` owns, not in an entity store, so `toggleCommentLike` and `updateComment` repeat the same *shape* — an optimistic write, a `stillOurs` reconcile, and a revert-and-rethrow — over a `List<Comment>` instead of a `Map<PostId, Post>`. Abstracting over "the container" would cost more than the twenty lines it saves. Keep the two implementations in step by reading both when you change one.
- **`SettingsRepository` emits one `Settings` snapshot, not one flow per setting.** An implementation supplies only `settings`; the per-setting flows are `map { … }.distinctUntilChanged()` defaults on the interface, so a reader of a single setting cannot forget the dedup and wake on every unrelated write. The snapshot is what `SettingsViewModel` reads, because a page showing several settings must never draw a pair that was never stored. Writes stay per-field — a `setSettings(Settings)` would be the read-modify-write shape that `PostRepository.updateEntity` exists to avoid. Adding a setting is a field on `Settings` plus its narrowed flow, and no new `combine` arm.
- `SettingsRepository` has two implementations: `InMemorySettingsRepository`, a test double, and `DataStoreSettingsRepository`, backed by Preferences DataStore. `DataStoreSettingsRepository` takes the DataStore as a constructor dependency, so unit tests can drive a real DataStore over a temporary file. The persisted key `theme_mode` is the contract with the legacy SharedPreferences file, and tests pin this key.
- **An absent preference means "never chosen."** What it resolves to is named once, in `DEFAULT_AUTO_PLAY_VIDEOS`, beside `Settings` in the domain layer, which is also where `Settings()` picks up its no-argument defaults. Auto-play is **off** when unset, because a feed that plays video by itself spends the user's data before the user asked for it. The repository and `HomeViewModel` both start from this same constant, so a launch never flips playback mid-load. Tests seed the value as the *opposite* of the default. This way, a read that falls through to the default fails the test instead of passing it by accident.
- Domain models, interfaces and every repository implementation carry no Android dependencies.

### Feature UI — the HOME feed sets the pattern

The app uses MVVM with unidirectional data flow. `HomeViewModel` exposes `StateFlow<HomeUiState>`, with states `Loading`, `Error`, and `Feed`. It converts user intent into repository mutations or `Navigator` pushes, both through one `HomeActions` seam.

**Two intent seams exist, on purpose — one is a pilot.** `HomeViewModel` and `ProfileViewModel` take intent through an actions interface the ViewModel implements. `PostDetailViewModel` takes it as one `PostDetailUiEvent` through an `eventSink` the *state itself* carries, so the stateless screen takes `(uiState, modifier)` and nothing else, whatever the page's seventeen intents grow to. `SettingsViewModel` is a half-step: sealed events, but the sink passed as a separate parameter.

`PostDetailUiState` is the shape the pilot is testing: one data class the ViewModel owns and edits with `copy`, with the load axis as a nested sealed `Content` field and everything orthogonal to it — the thread, the send states, the announcement — as ordinary fields beside it. Only the shared entity store stays reactive, through a single collector, because a like toggled on the feed or a delete performed on a profile has to reach the page unasked. **Do not migrate the other screens until this one has been lived with**; until then, follow whichever seam the screen you are editing already uses.

Every screen splits into two parts: a **stateful binder** that collects state and injects the ViewModel, and an **internal stateless composable** that takes pure inputs and callbacks. This split lets a screen preview and test with no ViewModel.

**A loaded feed outranks a load error.** When nothing is loaded, a failure becomes `HomeUiState.Error` and takes over the screen. When a feed is already loaded, a failure rides along as `Feed.refreshError` and shows in a snackbar instead, because a failed pull-to-refresh must not take away the posts the user is reading.

The transient error is **spent once shown**: `onRefreshErrorShown()` clears it, so a configuration change cannot show it again. `retry()` is the only path back to the full-screen error. Follow this pattern on any screen that can fail while it shows content.

**`profile/ui/`** uses the same split, parameterized by user through Hilt assisted injection, plus the `@CurrentUserId` signed-in user. A sticky tab row is generated from the `ProfileTab` enum: Posts, Likes, and Saved.

- **Saved is private, Likes is public.** `ProfileTab.ownerOnly` filters the tab row. This is why the selected index is `tabs.indexOf(selected)`, **not** the tab's ordinal. Client gating is only a courtesy. The server enforces the rule: `/bookmarks` returns 403 unless `:id` is the caller, and it refuses before the lookup, so an unknown id cannot distinguish itself from a real one.
- **Both lists load lazily on first open**, through `onSavedTabShown` and `onLikesTabShown`, not on `refresh`. A tab nobody opened costs no request. `bookmarkIds` and `likeIds` emit `null` until that first load. This is how a tab tells "empty" apart from "not asked yet." Refresh re-fetches only the tabs that were opened.
- **For the signed-in user, each list is derived from its flag, not echoed from the fetch.** `OnDemandPostIds` takes `Post::isBookmarked` or `Post::isLiked` and combines it with the entity store, so membership moves in both directions from anywhere, with no re-fetch. Deriving only *removal* is a trap to avoid. It would make membership depend on whether the post happened to be in an already-fetched page. Order comes from the server's keyset, `(createdAt, id)` descending, and the client reproduces this order. `PageState.oldestLoaded` holds back a post below the loaded window, instead of letting it jump ahead.
- None of this applies to **another** user's Likes tab, which is echoed exactly as fetched. `isLiked` and `isBookmarked` are viewer-scoped, so on someone else's profile they describe the viewer, not the profile owner. Saved and liked posts can be by anyone, so their authors ride along in `included.users` into `UserRepository`.

**There is exactly one user projection, and this fact matters.** A sideloaded author serializes identically to a fetched profile.

A past `minimal` projection was indistinguishable, on the wire, from a user who left those fields empty. Because `UserRepository.ingest` replaces entries wholesale, this silently blanked a fully-loaded profile's bio and counts.

The invariant on `User` is this: **a `null` optional field means the user left it empty, never "not loaded yet."** If a payload ever needs a trimmed user, give it its own type.

**`composer/ui/`**: `CreatePostViewModel` holds one plain `StateFlow<CreatePostUiState>`, with a form, an in-flight flag, and a single `CreatePostError`. It is not a sealed hierarchy, because a composer has nothing to load. There is one error field, not one per source, because the screen has a single snackbar.

Publishing goes through `FeedRepository.publish`, which stores the entity, ingests the author, and prepends the new ID to the feed. Then `Navigator.replaceTop` swaps in `Screen.PostDetail`, so backing out lands on the shell, not on a spent composer. A failed publish keeps the typed text. Leaving with a part-written post raises the shared `DiscardChangesDialog`.

- **Media is exclusive: photos or one video, never both.** `CreatePostMedia` and `NewPostMedia` are closed `None`/`Images`/`Video` hierarchies, so the illegal combination is *unrepresentable*. The server mirrors this rule with a `media_conflict` 422 response. The composer hides the other add-tile once one kind of media is chosen, which makes the exclusivity clear with no dialog.
- Limits mirror the server: `CreatePostMaxImages` is 10, and `CreatePostMaxVideoBytes` is 25 MB. The code checks the video size limit through `FileLoader.sizeOf` **before** it reads the file.
- Everything uploads through one multipart `POST /posts` request, with an `images[]` field (the brackets make Rack build an array) or a `video` field. `common/data/network/MultipartParts.kt` builds the parts, so a format change needs one edit, not one per data source.
- The form holds picked media as **content-URI strings, not bytes**. `FileLoader` reads them into `FileUpload` objects once, at publish time. This is why `CreatePostForm.toNewPost` takes the loaded media as a parameter. Re-picking media is additive, and the code de-duplicates it.
- **Video duration is the server's to derive.** The upload carries only the file. The composer's own thumbnail is the one exception, because nothing is uploaded yet at that point. `VideoMetadataReader` reads the duration locally, only for that badge, and degrades to 0 instead of blocking the post. This is why `CreatePostMedia.Video` carries a duration and `NewPostMedia.Video` does not.
- `PostApiMultipartTest` drives the **real Retrofit stack over MockWebServer** to pin the wire format. Part names and omitted optional parts are exactly where the client and the backend must agree.

### Dependency injection — Hilt

`MosaicApplication` is `@HiltAndroidApp`, not `MosaicApp`, the root *composable*. `MainActivity` is `@AndroidEntryPoint`. All bindings live in `app/di/{Data,Network,Navigation}Module.kt`.

Repositories are `@Singleton`, so the entity cache is shared across screens. They carry **no DI annotations themselves**, since the module constructs them, which keeps the data layer framework-free.

ViewModels are `@HiltViewModel`, bound through `hiltViewModel()` from `androidx.hilt:hilt-lifecycle-viewmodel-compose` (the `hilt-navigation-compose` copy is deprecated). Runtime arguments go through **assisted injection**.

Tests never touch Hilt. They construct ViewModels directly with fakes, which is why ViewModels keep plain constructor parameters.

### Theming

`app/theme/` implements the **Mosaic** design system: a fixed brand palette, with an indigo accent, warm-neutral surfaces, and a coral like-state color, with full light and dark tokens in `Color.kt`.

**The app uses no dynamic color**, so the brand stays consistent. `surfaceTint` is transparent, so cards keep their exact color while still casting a shadow.

`Type.kt` wires two bundled variable fonts, in `res/font/` with an OFL license in `licenses/`: Bricolage Grotesque for the wordmark and post titles, and Manrope for UI text and body text. Tokens with no Material role, for example `textTertiary` and `like`, ride on `MosaicColors` through `LocalMosaicColors`.

**Wrap any new top-level Compose content, and every `@Preview`, in `MosaicTheme`.** A `DisposableEffect`, keyed on the resolved theme, re-applies edge-to-edge bar styling.

## Surviving process death

**Rotation is the same problem, one step weaker.** A configuration change wipes anything held in a plain `remember`. Only ViewModels and repositories survive it.

The rule is this: **any state a composable owns outright is `rememberSaveable`, not `remember`.** This includes whether a sheet or dialog is open, so a rotation mid-report does not throw the report away. `remember` stays correct for state that is meaningless after recreation, for example an `Animatable` or a transient gesture offset.

**The back stack comes back. Nothing else does.** `rememberBackStack` saves the `@Serializable` entries, and `rememberSaveableStateHolderNavEntryDecorator` restores each entry's saveable state under that same identity. This is why the id must be a stored property, not a regenerated default.

Every repository, ViewModel, and store gets rebuilt from scratch. So **a screen must be able to rebuild everything it shows from its `Screen` key alone.** Anything that cannot be re-derived goes on the key: `AlbumViewer` carries its image URLs, and `FullscreenVideo` carries its URL and title.

For a page that fetches data, this means a **cold-start load**. Check whether the stores already hold what the key names, and fetch only when they do not, for example `EditProfileViewModel.load` and `PostDetailViewModel.loadPost` through `PostRepository.load`. A page opened the ordinary way skips the request.

**An absent entity is three states, not one**, on every screen that resolves one by id. Reading "the store has no such thing" as *not found* is correct only while the store is authoritative, and that stops being true the moment the app can start directly on that page.

`PostDetailViewModel.PostFetch` separates three states: `Loading`, `NotFound` (a 404, with nothing to retry), and `Error` (retryable). `PostDataSource.fetch` returns `null` for a 404 through `notFoundAsNull`, instead of throwing. `ProfileViewModel` gates `NotFound` behind `_hasLoaded`, for the same reason.

**Deletion states its result explicitly**, because absence alone is ambiguous. `PostRepository.delete` records the ID in `deletedIds` before it drops the entity. So a detail page whose post was deleted from another screen reads `NotFound`, instead of spinning on a fetch that will never come.

`load` short-circuits on the same set, so "deleted" stays true even offline. Feed and profile need no such check. They resolve IDs through `entities` and drop whatever vanishes.

Both endpoints that answer with a *single* post embed the author. So `PostRepository` seeds `UserRepository` from `load` and from `create`. A sideloaded user belongs in the user store, no matter where it arrives from.

**What the user typed is the one thing that must be *saved*, not re-derived.** `CreatePostViewModel` and `EditProfileViewModel` persist their form through `SavedStateHandle`, scoped per back-stack entry.

`app/util/SavedDraft.kt` is the seam. `saveDraft` registers *where to read the draft from*, as a `() -> T`, not a `Flow<T>`, so the platform pulls the value at most once per save, and typing costs nothing. Serializing the whole form is what keeps `CreatePostMedia`'s variant, and with it the photos-or-video exclusivity, intact.

Only the *form* is saved. An in-flight publish and errors from a dead process are not saved, so a restored composer is idle. The editor re-reads its pristine snapshot from the server, so restored edits still read as unsaved.

**These tests are instrumented, on purpose.** A `SavedState` is a `Bundle`, so a JVM test could exercise only a stand-in.

`BackStackRestorationTest` walks one of every `Screen` through `emulateSavedInstanceStateRestore()`, which catches a key that quietly stops being serializable, and it pins entry identity across the restore. `DraftRestorationTest` does the round trip: save, fresh handle, rebuild.

ViewModel unit tests cover cold-start loading, since neither test type can emulate empty stores.

Video playback position is deliberately not restored.

## Compose performance

Strong skipping is on, through the built-in Compose compiler in Kotlin 2.4.10. This shapes what is worth doing:

- **Do not wrap lambdas in `remember` by reflex, or chase `@Stable`/`@Immutable`.** Strong skipping auto-remembers lambdas, and it lets composables with unstable parameters skip through instance equality. Feed rows skip fine, even though `Post` carries an `Instant` and a `List`.
- **Skipping works by instance, so a mutation must preserve identity for everything unchanged.** `PostRepository` stores a `Map<PostId, Post>` and mutates it with `_entities.update { it + (postId to updated) }`, so a like recomposes exactly one `PostCard`. Mapping `copy()` over the whole collection would recompose every visible row. Avoid that.
- **Read snapshot state in the smallest composable that uses it.** Reading scroll or pager state at a screen's top level invalidates the whole scope. The album viewer keeps its page counter in a separate `PageIndicator`, so swiping recomposes only the pill.
- **Coalesce continuous signals with `derivedStateOf`**, for example a scroll offset into a boolean, so readers recompose only when the result flips. This is not needed for a signal that is already coalesced. `HomeScreen` reads `listState.canScrollBackward` directly.
- **Do not key a long-lived effect on state you only read inside it.** The feed's autoplay collector reads posts through `rememberUpdatedState`, and keys only on the stable `listState` and playback state. The auto-play *setting* is a key on purpose. The restart re-reads the current layout, so switching auto-play on plays the video already on screen.
- **Animate in the draw or layer phase where you can.** The like "pop" animation reads its `Animatable` inside `graphicsLayer`, so each frame re-runs the layer phase, not composition. Composition-scope animation is still the right choice for short transitions of small subtrees.

## Code style

**ktlint does not enforce these rules, on purpose. This section is the only place they are stated.** `.editorconfig` disables the `function-signature` and `multiline-expression-wrapping` rules, because their wrapping modes conflict with the brace and parameter rules below. The rest of this section has no ktlint rule at all. Do not delete this section on the assumption that the formatter already covers it.

**Separate logical sections within a function with a blank line.** Group declarations together, then leave a blank line before the code that uses them. This applies to plain functions and to composables alike.

```kotlin
@Composable
private fun OverflowMenu(...) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    IconButton(...) { ... }

    if (showSheet) {
        PostOverflowSheet(...)
    }
}
```

**Break function parameters onto separate lines when there are three or more**, with a trailing comma.

```kotlin
// 2 params — inline is fine
fun FeedTopBar(elevated: Boolean, onOpenSettings: () -> Unit)

// 3+ params — one per line
fun HomeScreen(
    uiState: HomeUiState,
    isRefreshing: Boolean,
    actions: HomeActions,
    onOpenSettings: () -> Unit,
)
```

**An expression body that ends in a trailing lambda keeps its brace on the declaration line.** Never break the line after the `=`. Break *inside* the lambda instead.

```kotlin
// Yes
override fun onToggleLike(postId: PostId) = launchCatching {
    postRepository.toggleLike(postId)
}

// No
override fun onToggleLike(postId: PostId) =
    launchCatching { postRepository.toggleLike(postId) }
```

A **block of work**, for example anything launched, a coroutine body, or a test body, always takes its own lines. This way a ViewModel's actions all read as one shape. A **small value expression** stays inline until it no longer fits on one line:

```kotlin
override fun onNicknameChange(value: String) = updateForm { it.copy(nickname = value) }

override fun onAgeChange(value: String) = updateForm { form ->
    form.copy(age = value.filter { it.isDigit() }.take(3))
}
```

**Use named arguments when the value does not self-document its role**: bare literals, arguments of the same type, or anything opaque without context. Two cases always call for named arguments:

- *Lambda type parameters*: name them at the type declaration, for example `(Album, initialIndex: Int) -> Unit`, not `(Album, Int) -> Unit`.
- *Coordinate and geometry constructors*, for example `Offset(x = 0f, y = thickness / 2f)`, because `x`/`y` and `width`/`height` are easy to transpose by mistake.

Exception: enum constructor entries. Kotlin does not allow named arguments there.

**A file-level overview comment is a block comment, `/* … */`, not a KDoc.** Otherwise ktlint's `no-consecutive-comments` rule fails the build.

**Never divide a file with a banner comment**, for example `// ── Intent ─────────────────`. A rule of box-drawing characters, dashes, or equals signs is not a section header, it is a table of contents nobody updates. It goes stale the moment a declaration moves, it hides the fact that the file has outgrown itself, and it makes every diff that touches the boundary noisier than the change.

```kotlin
// No
// ── The thread ────────────────────────────────────────────────────────────
private fun loadComments() { … }

// Yes
private fun loadComments() { … }
```

A file that feels like it needs banners is telling you something. **Split it, or accept it as one long file.** Declaration order and blank lines carry the grouping. Where a group genuinely needs a word of explanation, that word is KDoc on the first declaration, which the IDE shows at every call site; a banner shows nowhere.

This does not ban ordinary comments. `//` on a line that explains *why* is still welcome. The rule is about the decoration.

## Localization

All user-facing text lives in `res/values/strings.xml` and is read with `stringResource(...)`. **Never hardcode a display string in Kotlin.** Exception: a string with no words at all, for example `"$page / $total"`, is fine as plain interpolation.

Navigation labels are `@StringRes` IDs on `ShellDestinations`. Post content in `SampleData` is stand-in data, not app chrome, so it stays literal.

Computed text keeps its *logic* pure. `relativeTime()` and `compactCount()`, in `app/util`, return structured buckets with no display strings. `common/Formatting.kt`'s `asText()` resolves a bucket to a localized resource.

Anything counted needs `<plurals>` and `pluralStringResource(...)`, not `%d`, because Czech buckets counts into `one`, `few`, `many`, and `other`.

Strings that stay identical in every locale, for example the `Mosaic` wordmark and `English`/`Čeština`, are marked `translatable="false"`.

**The app ships two languages: English (`values/`, the default) and Czech (`values-cs/`).** Add a language with a `values-<code>` folder plus an `AppLanguage` entry.

**The language is a setting like any other: `SettingsRepository` stores it, and the platform locale is a *projection* of what it holds.** The language belongs beside the theme because that is what it is to the user, and keeping one store means the settings page reads one snapshot. `AppLocaleRepository` no longer holds the value — it only pushes it down (`applyLanguage`) and seeds it on a first launch (`resolveInitialLanguage`).

The one collector lives in `MainViewModel`'s `init`, over `settingsRepository.language`. This is the exception to the "no locale calls in a ViewModel `init`" rule below, and it is safe for a specific reason: the first value arrives from DataStore, so nothing touches the delegate during construction. **Never call `AppCompatDelegate` from anywhere else.** One collector is what makes storing and applying impossible to get out of step, and `applyLanguage` is a no-op when the language is already in effect, so a launch does not recreate the Activity for a value the platform already has.

**The app deliberately does not appear under the system's per-app language settings.** It declares no `android:localeConfig` and no `generateLocaleConfig`. The in-app picker is the only way to change the language, so nothing writes the platform locale behind the app's back.

**Do not re-enable `generateLocaleConfig`** unless you also make `resolveInitialLanguage` reconcile an externally-changed `AppCompatDelegate.getApplicationLocales()` back into `SettingsRepository` on every activity create.

Applying uses the per-app language APIs. `AppCompatLocaleRepository` calls `AppCompatDelegate.setApplicationLocales(…)`, which forwards to `LocaleManager` on Android 13 and above, and stores the choice itself below that version. That backport is why `androidx.appcompat` is a dependency, why `MainActivity` extends `AppCompatActivity`, why `Theme.Mosaic` descends from `Theme.AppCompat`, and why the manifest declares `AppLocalesMetadataHolderService` with `autoStoreLocales=true`. `InMemoryAppLocaleRepository` is the test double, and it records what reached the platform in `applied`.

**The platform therefore keeps its own copy, and that is deliberate, not a duplicate to clean up.** It is the copy the platform re-applies *before* the Activity exists, which is what stops a cold start drawing a frame in the wrong language; DataStore is read too late for that. The app's store is the answer, the platform's is a cache. They can only disagree on an upgrade from a build that predated the setting, which is why `resolveInitialLanguage` seeds from the platform copy before falling back to the device's languages — otherwise upgrading would silently re-resolve a language the user had already picked.

**There is no "System" language option, on purpose.** With only two languages shipped, a device set to a third language would silently mean "English" with no visible reason. Instead, the device seeds the language choice **once**.

On first launch, `MainActivity` calls `MainViewModel.resolveInitialAppLanguage()`. This method picks the first device-preferred language the app ships, through `AppLanguage.fromLanguageTags`, ignoring region and Unicode-extension subtags, and falls back to English. It *stores* that choice and applies nothing; the collector does the applying. It is a no-op once a language is stored, which keeps the app steady when the device's language changes later.

**`Settings.language` is nullable, and the null is load-bearing**: it is what tells "never chosen" from "chose English", which is the whole condition `resolveInitialLanguage` turns on. Do not give it a non-null default. `SettingsViewModel` resolves it to `AppLanguage.Default` for display only.

The call stays in `MainActivity.onCreate`, **after `super.onCreate`**, because the locale APIs need the AppCompat delegate attached. It does not live in a ViewModel `init` block.
