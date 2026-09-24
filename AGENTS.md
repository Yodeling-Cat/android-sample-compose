# AGENTS.md

Guidance for AI agents working in this repository.

## Project purpose

This is a sample Android app (`uno.lux.mosaic`, shown as "Mosaic"). It is a resume and portfolio piece. It shows modern Android architecture.

Code quality, idiomatic Compose, and clear architecture are the goal. Working features alone are not enough. Always prefer the current recommended Android method over a quick shortcut.

## Testing philosophy — tests first

Tests are the **primary consumer** of this codebase. Users come second.

- **Write the test first.** A change is not done until tests cover it and the tests pass. New logic must land with its test in the same change.
- **Design for the test.** Logic lives in plain-JVM units, with constructor injection and no Android dependencies. Pure functions take their inputs (for example `now`) as parameters instead of reading ambient state. This is *why* the architecture has this shape: it makes the code testable.
- **Same rule for UI.** Stateless composables take data and callbacks as parameters. ViewModels expose a `StateFlow` that a test can assert against.

Shared test infrastructure lives in `app/src/test/java/uno/lux/mosaic/testing/`. It includes `MainDispatcherRule` (swaps `Dispatchers.Main`), `ViewModelTest` (a base class for ViewModel tests), `BackStacks` (`backStackOf`/`screens`, for driving the `Navigator`), and `TestUtils`.

Fakes are hand-written. Each fake lives beside the thing it stands in for, for example `post/data/FakePostDataSource.kt`. The project uses no mocking framework.

**A helper two modules' tests need is a test fixture, not a copy.** `:core:common` publishes `WireTestHelpers` (`httpException`, `notFoundException`, `emptyPage`) and `FakeFileLoader` through `src/testFixtures`, and `:app` takes them with `testImplementation(testFixtures(project(":core:common")))`. A fixture is its own compilation, so nothing in it may be `internal`. `MockApiServer` is deliberately *not* a fixture: it builds its Retrofit from `app/di`'s `NetworkModule`, which is the one direction `common` may not know, so it sits in `:app`'s own `testing/` package with the rest of the shared test infrastructure.

## Commands

The shell is Windows PowerShell. Invoke the wrapper as `.\gradlew.bat`.

**Every variant task name carries a `server` flavor** (see *Which server a build talks to*). `remote` is the default flavor, and the one every command below names.

- Unit tests: `.\gradlew.bat testRemoteDebugUnitTest testDebugUnitTest`. Both task names are needed and neither is redundant — `:app`'s tests are flavored and the two library modules' are not, so from the root each name runs in whichever projects have it. For one class, add `--tests "uno.lux.mosaic.post.data.PostRepositoryTest"`. Add `.<method>` to run one test.
- Lint: `.\gradlew.bat lintRemoteDebug lintDebug`, for the app and the libraries. The app's report is at `app/build/reports/lint-results-remoteDebug.html`. For formatting, use `.\gradlew.bat ktlintCheck`, which already covers every module.
- Build or install: `.\gradlew.bat assembleRemoteDebug` or `.\gradlew.bat installRemoteDebug`.
- Compose stability reports: `.\gradlew.bat -Pmosaic.composeReports compileRemoteReleaseKotlin --rerun compileReleaseKotlin --rerun`. It writes `*-classes.txt` and `*-composables.txt` to each module's `build/compose-reports/`. Both task names are needed, for the same reason as the unit tests. Keep each `--rerun`: the reports are not a tracked output, so a compile task that is up to date or restored from the build cache writes none. See *Compose performance* for when to run it and what to look for.
- Instrumented tests (need a device): `.\gradlew.bat connectedRemoteDebugAndroidTest`. For one class, add `-Pandroid.testInstrumentationRunnerArguments.class=…`.

**CI** (`.github/workflows/ci.yml`) runs `ktlintCheck`, `lintRemoteDebug lintDebug`, and `testRemoteDebugUnitTest testDebugUnitTest` on every push to main and every pull request, then compiles the instrumented suite with `compileRemoteDebugAndroidTestKotlin` without running it — nothing else type-checks `androidTest`. It uploads the reports as artifacts. These JVM-only checks are the checks that gate a change.

**There is no emulator job.** No automatic process runs the instrumented suite. A change to process-death or back-stack behavior needs the user to run it.

**Do not touch a real device or emulator unless the user asks.** This rule covers `connectedRemoteDebugAndroidTest`, `installRemoteDebug`, `adb`, and driving the app by hand.

Run the three JVM-only checks instead. State plainly which parts of a change they cover and which parts need a device. Write the instrumented test when the behavior needs one. Leave running it to the user.

## Toolchain / build setup

- **Kotlin 2.4.20**, **AGP 9.4.1**, **Gradle 9.7.1**, Compose BOM **2026.09.00**. Java 11 is the source and target version. The Gradle daemon runs on JDK 21.
- `compileSdk` and `targetSdk` are both **37** (the `release(37)` DSL). `minSdk` is **26**. `compileSdk` and `minSdk` are stated **once**, in `build-logic`'s `configureAndroid`, which the application and the libraries both go through — compiling a library against a different SDK than the app that ships it is a bug waiting for a release. `targetSdk` is the exception and stays in `app/build.gradle.kts`: only an application has one, and bumping it is the reviewed decision described above. The project bumps these two settings separately, on purpose. A new SDK version lands on `compileSdk` first. It moves to `targetSdk` only after a review of its behavior changes. That review is cheap here for five reasons: the app declares only the `INTERNET` permission, it picks media through `PickVisualMedia`, it runs no foreground service, it is already edge-to-edge, and it ships no native code. Check these five items again when the next SDK version lands.
- The project uses AGP 9's **built-in Kotlin**, so it has no `org.jetbrains.kotlin.android` plugin. Annotation processing uses **KSP**, because kapt is not compatible. Hilt runs on KSP too.
- AGP bundles Kotlin 2.2.10. The root `build.gradle.kts` raises the compiler version to 2.4.20 through the **buildscript classpath**. This is the documented way to override it. The Compose compiler, kotlinx-serialization, and **Mappie** are locked to the Kotlin version. **A Kotlin version bump is blocked until Mappie publishes a matching build.** KSP versions on its own schedule (`2.3.12`).
- Add dependencies to the **version catalog** (`gradle/libs.versions.toml`) and reference them through `libs.*`. Never hardcode a version number in a build script. `build-logic` reads the same catalog through its own `settings.gradle.kts`, so the rule holds inside the convention plugins too.

## Backend

The backend is a **Ruby on Rails** app. `NetworkModule` holds the host as `BASE_URL` and builds `API_URL` from it.

### Which server a build talks to

`BASE_URL` is a `buildConfigField`, set by the **`server` flavor dimension** in `app/build.gradle.kts`. There is no in-app switch, and both flavors keep the one `applicationId`, so swapping servers is the Build Variants dropdown — or a `Remote`/`Local` task name — and never a reinstall under a different package.

- **`remote`** (`isDefault`) — the deployed host, `https://mosaic.tree-among-shrubs.com`.
- **`local`** — `http://localhost:3000`, reached through an adb reverse tunnel.

**The tunnel opens itself.** `adbReverseLocalServer` (`AdbReverseTask` in `build-logic`'s `AdbReverse.kt`, wired up by one call at the end of `app/build.gradle.kts`) runs `adb reverse tcp:3000 tcp:3000` for every attached device, and it finalizes every `assembleLocal*` and `installLocal*` task. It hangs off *assemble* rather than install because Android Studio's Run button deploys the APK itself and never calls the install task.

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

**The report dialog waits for that 204 anyway.** It stays up for the whole request with Send disabled, closes on the answer, and only then shows the thanks; a failure is stated in the dialog, which is still on screen with the reason and details the user picked.

**The ViewModel owns the dialog, not the card.** Its lifetime *is* the request's, so it is one value, `PostReport?`: `null`, `Open(postId, send)`, or `Sent` while the thanks is still to be shown. `PostReporter` in `post/ui/PostReporter.kt` holds it and the request's `Job`, and each of the three reporting ViewModels composes one and forwards `OpenReport`, `SendReport`, `CloseReport` and `ReportSentShown` to it. Each screen draws it once, through `ReportHost`; a card only raises `OpenReport`. Keeping the open flag in the composable and the send state in the ViewModel is the shape to avoid: two owners of one lifetime need a callback to keep them in step. `close` cancels a send still out, so an answer nobody is waiting for cannot settle after the dialog. An open report dialog does not survive process death, which is accepted: a request from a dead process has no answer to wait for either. A report is not a `FailedAction`: that enum is for a tap whose UI is gone by the time the server answers, and this one's is not.

On `PostDetailViewModel` the report is its own `report` flow beside `uiState`, not a field of it, because folding a holder's flow into one data class takes a second collector that runs a dispatch behind the send.

The reason's *wire* spelling lives in `post/data/network/ReportPostRequestDto.kt`. This keeps the domain enum free of wire concerns. Blank details are left out of the request body rather than sent as an empty string.

## Package structure — where a new file goes

The top level of the package tree is **slices, not layers**. Layers are the shape *inside* a slice. There is no root `data/` or `ui/` package, and, on purpose, no `activities/`, `viewmodels/`, or `dialogs/` package. **A file's location depends on what it is about, never on what class it extends.**

```
:app
  post/ user/ comment/ album/ video/               aggregates: the entity, its wire types, its store, its UI
  feed/ home/ profile/ composer/ settings/ shell/  features and read models — they own no entity
  app/                 the machine — MainActivity, MosaicApplication, MosaicApp
  app/di/              Hilt modules
  app/navigation/      Navigator, Screen keys, BackStackEntry, page transitions
  app/fixtures/        stand-in content for previews and DI seeding

:core:common
  common/ui/           noun-free composables two or more concerns need
  common/data/         wire types that describe no single aggregate, plus file loading
  common/util/         pure functions with zero project imports, plus composables that draw nothing

:core:design-system
  designsystem/theme/       the Mosaic palette, type and gradients
  designsystem/components/  branded controls, no domain noun
```

**Three Gradle modules, and the arrows only point one way**: `:app` depends on `:core:common`, which depends on `:core:design-system`. See *Three modules, on purpose* below for why these two came out and nothing else did.

Inside a concern there are four layers and no fifth:

```
<concern>/data/            the repository and its DataSource interface
<concern>/data/domain/     the models — pure Kotlin, no platform and no wire
<concern>/data/network/    the service, DTOs, mappers — the only place HTTP appears
<concern>/ui/              composables and ViewModels
```

**`designsystem/` versus `common/ui/`**: both hold noun-free composables. The difference is whose vocabulary the composable belongs to. `designsystem/components/` is the branded design system, for example `HoldToConfirmButton` — it knows nothing of this app beyond its brand, and could ship to a second one unchanged. `common/ui/` is noun-free UI that **two or more** concerns actually need, for example `FullScreenError` and `DiscardChangesDialog`. Being noun-free is not enough to earn a place in `common/`. A generic composable with one consumer belongs to that consumer's slice. Move it to `common/` only on the day a second consumer needs it.

**A resource lives in the module that draws it**, and when two modules draw it, in the lower one. R classes are non-transitive, so `:app` names a resource it shares with `common` through `uno.lux.mosaic.common.R as CommonR` — `ic_arrow_back` and `navigate_back` are the current examples. A manifest or XML reference needs no alias, because those resolve by name when the resources merge.

**Aggregates versus read models** keep the dependency graph directed. `post`, `user`, `comment`, `album`, and `video` each own an entity. `feed` and `profile` own no entity. They hold ordered *IDs* and paging state, and resolve the IDs through the entity stores. This is why `ProfileRepository` *composes* `PostRepository`.

> Features may depend on aggregates. **Aggregates never depend on features.**

```
user  album  video  settings  -> (nothing)
post      -> album comment user video      comment  -> post user
feed      -> post user                     profile  -> post user video
home      -> feed post settings user video
composer  -> feed post                     shell    -> home profile user
```

Every slice may depend on `app`. `app` wires everything together. `common/` and `designsystem/` import no slice — which is now a compile error rather than a convention, since they are modules that cannot see `:app`.

The feature-to-feature edges are deliberate. `home -> feed`: the Home tab is the screen that shows the feed, while `feed` keeps only its ordered IDs and paging. `composer -> feed`: publishing a post prepends the new ID to the feed. `home -> settings`: auto-play reads a setting. `shell -> home profile`: its tabs *are* those screens.

`post <-> comment` is a real cycle, and the project tolerates it. If it ever causes a problem, the fix is to fold `comment/` into `post/`.

**To file a new file, ask in order:**

1. **Does it mention a domain noun?** No → 2. Yes → 3.
2. Pixels → branded control → `designsystem/components`; color/font → `designsystem/theme`; needed by 2+ concerns → `common/ui`; one concern → that concern. Draws nothing → `common/util`, even when `@Composable` — **unless the design system itself needs it**, in which case it goes there, because `:core:design-system` sits below `:core:common` and cannot import it. `ClickDebounce` is the one case today. Wires the app together → `app`. Neither → `common/data`.
3. Claimed by exactly one feature → that feature. By several → the noun's aggregate.

The moment a "helper" imports `Post`, it is post code. File it in `post/`.

**Two things look like violations but are not.** First, a cross-slice KDoc reference is written fully qualified, for example `[uno.lux.mosaic.profile.data.ProfileRepository]`, because a `[Link]` needs an import. Second, a wire type shared by two features belongs to the aggregate it describes, for example `SideloadedUsers` belongs in `user/data/network/`. When it describes no single aggregate, it belongs in `common/data/network/`, for example `LikeToggleDto`.

**The `DataSource` interface stays beside its consumer, not its implementation.** This lets a repository compose a network source and a local source without either one owning the contract. It is also why `data/network/` nests inside the slice.

**Use one Retrofit service per slice**: `FeedApi`, `PostApi`, `CommentApi`, `UserApi`, `ProfileApi`. All come from the single `Retrofit` instance in `app/di/NetworkModule.kt`.

Do not bring back an app-wide API interface. The previous one fit in no slice. Its 338-line fake had to be implemented in full by every test that needed only one endpoint.

### ArchitectureTest

`app/src/test/java/uno/lux/mosaic/architecture/ArchitectureTest.kt` (Konsist) checks these rules against the real source tree. It fails the build on a violation. **Every rule comes from the file path, never from a list of names.** The test finds concerns as the top-level packages other than `app`, `common` and `designsystem`, so adding a slice needs no edit to the test.

Konsist scans every module, so the rules reach `:core:common` and `:core:design-system` too. `build-logic` is filtered out: its convention plugins carry no package at all, and left in, each one reads as a concern named `""`.

1. **Every concern package follows the convention** of the four layers. The other rules select files by these layer suffixes. This rule guards against a rule silently matching nothing.
2. **The wire stays in `data/network`.** Retrofit and OkHttp appear nowhere else, except `app/di`, for the one `Retrofit` instance.
3. **The domain layer is pure.** `data/domain` imports no platform code, no wire type, and no screen. Mappers convert only from DTO to model.
4. **`data` never depends on `ui`.**
5. **`common` knows no concern.**
6. **The design system knows no concern.** Gradle already stops `:core:design-system` from importing `:app`; what this rule adds is the direction Gradle cannot see — a concern dragged *into* the design system, for example a branded control that grew a `Post` parameter. `common/util` is covered by rule 5 instead, now that it lives in `common`.
7. **A repository with no interface stays plain-JVM.** A `*Repository` class with no supertype carries no Android import. `DataStoreSettingsRepository` and `AppCompatLocaleRepository` may touch the platform, because their consumers can be handed a test double instead.
8. **A screen's ViewModel takes intent only through `onEvent`.** A `*ViewModel` in a concern's `ui/` package extends only `ViewModel`, and `onEvent` is its only public function. `MainViewModel` lives in `app/ui`, so the path puts it outside the rule. Overrides are left to the supertype check, because an `override` with no written modifier inherits `protected`, and Konsist reads `onCleared` as public.

The test does **not** check the direction of the cross-concern graph, on purpose. Encoding which cycles are tolerated would cost more than the code review that catches them.

Adding a rule means adding a test. Verify a new rule by planting a violation and watching the test fail. If a violation is *intended*, widen the rule and explain why in its failure message, instead of deleting the rule.

## Three modules, on purpose

**The build is `:app`, `:core:common` and `:core:design-system`, and every concern stays inside `:app`.** Do not move a concern into its own module by accident. The slices map onto module boundaries almost mechanically, so more of a split stays possible later.

**Why these two came out.** Both are leaves: they own no entity and import no concern, so extracting them needed no architectural change at all — no navigation rework, no cycle to break, and not one call site rewritten beyond its import. What it bought is that `internal` and the dependency arrows became the compiler's problem instead of a reviewer's. The extraction also measured how much the single module had been giving away: **every** `internal` in `common` turned out to be app-facing and had to be made public, because in one module `internal` had never meant anything narrower than "the whole app".

**Why the concerns stay put.** Splitting `post`, `feed` and the rest would cost the exhaustive `when` over the sealed `Screen` — feature modules cannot see each other's screens, which pushes navigation toward runtime route registration — and would force `post <-> comment` to be resolved rather than tolerated. Build avoidance is not a reason to do it either: KSP and Hilt codegen and configuration dominate build time here, not the recompiling of unrelated Kotlin, and every new module adds its own round of both.

**Revisit this decision when** one of three things happens: boundaries are violated in practice and code review does not catch it, build times become a real complaint as measured with `--profile`, or a second contributor joins the project.

**Build configuration lives in `build-logic`**, an included build with three convention plugins — `mosaic.android.application`, `mosaic.android.library` and `mosaic.android.library.compose`. The SDK levels, the Java version, ktlint and the Compose setup are stated once each, in `AndroidConventions.kt`'s `configureAndroid` and `configureCompose`, which take AGP's `CommonExtension` so the application and the libraries share them. **`:app` goes through the same plugin**, on purpose: settings centralized for the libraries but restated by the app would be exactly the drift centralizing them was meant to stop. A module's build file is then its namespace, what only it needs, and its dependencies. Versions still come from the catalog: `Catalog.kt` reaches it through `VersionCatalogsExtension`, because the generated `libs.*` accessors exist only inside a build script.

## Architecture

The app is single-activity and 100% Compose. It uses no Fragments and no XML layouts. XML under `res/` holds resources only.

### Entry point

`MainActivity` (`app/ui/MainActivity.kt`) is the only Activity, and it stays a thin shell. It collects `ThemeMode` from `MainViewModel`, resolves dark or light mode, applies the result to `MosaicTheme`, and hosts `MosaicApp()`.

It extends `AppCompatActivity` for **one reason only**: AppCompat's delegate applies the per-app language on devices below Android 13 (see *Localization*). The app uses no AppCompat UI.

### Navigation — Navigation 3, driven by ViewModels

`MosaicApp()` owns a `rememberBackStack` of `@Serializable` `BackStackEntry` keys. Each key pairs a `Screen` with the identity its state is scoped to. `Screen.Shell` is the permanent root. `Screen.Profile(userId)` and `Screen.Settings` push over it.

`NavDisplay` renders the top entry, using the push and pop specs in `app/navigation/PageTransitions.kt`, and it includes predictive back. Back handling is `onBack` popping the stack. **The code uses no hand-rolled `BackHandler` to pop it.** The shell's return-to-Home handler is the one exception, and it is not a pop: see *The shell*. Entry decorators give each entry its own saveable state and `ViewModelStore`, so a pushed page's ViewModel is created on push and cleared on pop.

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

- **Tab selection is plain `rememberSaveable`, not back-stack entries.** Switching tabs is not a navigation event, so the system back button never walks the tab history. **Back from any tab but Home returns to Home**, through a `BackHandler` in the stateless `ShellScreen` that is enabled only off Home, and back from Home leaves the shell. `ShellBackTest` (instrumented) pins this.
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

The app uses MVVM with unidirectional data flow. `HomeViewModel` exposes `StateFlow<HomeUiState>`, with states `Loading`, `Error`, and `Feed`. It takes every user intent as one `HomeUiEvent` through `onEvent`, and turns it into a repository mutation or a `Navigator` push.

**Every screen takes intent the same way: one sealed `<Screen>UiEvent`, sent through `onEvent`.** The ViewModel's only public function is `fun onEvent(event: HomeUiEvent): Unit = when (event) { … }`, and every handler behind it is private. `ArchitectureTest` enforces this. The stateless screen takes its state and `onEvent: (HomeUiEvent) -> Unit`, so a preview passes `onEvent = {}`, and a UI test can pass `events::add` and assert on exactly what was sent. An event that carries an argument is a `data class`, and one without is a `data object`. Name an event for what the user asked for (`ToggleLike`, `OpenPost`), or for what happened to the screen (`ImagesPicked`, `FailedActionShown`).

**The sink is a parameter beside the state, never a field inside it.** `HomeUiState` and the other load states are sealed, and a sink inside them would have to ride on every case. A lambda in a data class also makes equality depend on one instance surviving every `copy`, and makes a test build a sink just to write an expected state.

**Leaf composables never take the sink.** `PostCard` takes plain lambdas, and each screen turns them into its own events at the call site. That is what lets Home, Profile and the detail page share it. The shell, the album viewer and the video page keep their stateless `onBack`-style lambdas for the same reason, and their binders turn those into events. The system pickers in the composer and the profile editor stay separate `onPick…` parameters, because launching one needs a launcher from the composition, not a ViewModel.

**`PostDetailUiState` is a pilot for the *state* shape, not for the event seam.** It tests one data class the ViewModel owns and edits with `copy`, with the load axis as a nested sealed `Content` field and everything orthogonal to it — the thread, the send states, the announcement — as ordinary fields beside it. Only the shared entity store stays reactive, through a single collector, because a like toggled on the feed or a delete performed on a profile has to reach the page unasked. **Do not migrate the other screens' state until this one has been lived with**; until then, keep each screen's state in the shape it already has.

Every screen splits into two parts: a **stateful binder** that collects state and injects the ViewModel, and an **internal stateless composable** that takes its state and `onEvent`. This split lets a screen preview and test with no ViewModel.

**A loaded feed outranks a load error.** When nothing is loaded, a failure becomes `HomeUiState.Error` and takes over the screen. When a feed is already loaded, a failure rides along as `Feed.refreshError` and shows in a snackbar instead, because a failed pull-to-refresh must not take away the posts the user is reading.

The transient error is **spent once shown**: `HomeUiEvent.RefreshErrorShown` clears it, so a configuration change cannot show it again. `HomeUiEvent.Retry` is the only path back to the full-screen error. Follow this pattern on any screen that can fail while it shows content.

**`profile/ui/`** uses the same split, parameterized by user through Hilt assisted injection, plus the `@CurrentUserId` signed-in user. A sticky tab row is generated from the `ProfileTab` enum: Posts, Likes, and Saved.

- **Saved is private, Likes is public.** `ProfileTab.ownerOnly` filters the tab row. This is why the selected index is `tabs.indexOf(selected)`, **not** the tab's ordinal. Client gating is only a courtesy. The server enforces the rule: `/bookmarks` returns 403 unless `:id` is the caller, and it refuses before the lookup, so an unknown id cannot distinguish itself from a real one.
- **Both lists load lazily on first open**, through `ProfileUiEvent.SavedTabShown` and `LikesTabShown`, not on `Refresh`. A tab nobody opened costs no request. `bookmarkIds` and `likeIds` emit `null` until that first load. This is how a tab tells "empty" apart from "not asked yet." Refresh re-fetches only the tabs that were opened.
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

`:core:design-system` implements the **Mosaic** design system: a fixed brand palette, with an indigo accent, warm-neutral surfaces, and a coral like-state color, with full light and dark tokens in `designsystem/theme/Color.kt`.

**The app uses no dynamic color**, so the brand stays consistent. `surfaceTint` is transparent, so cards keep their exact color while still casting a shadow.

`Typography.kt` wires two bundled variable fonts, in the module's own `res/font/` with an OFL license in the repository's `licenses/`: Bricolage Grotesque for the wordmark and post titles, and Manrope for UI text and body text. Tokens with no Material role, for example `textTertiary` and `like`, ride on `MosaicColors` through `LocalMosaicColors`.

**Wrap any new top-level Compose content, and every `@Preview`, in `MosaicTheme`.** A `DisposableEffect`, keyed on the resolved theme, re-applies edge-to-edge bar styling.

## Surviving process death

**Rotation is the same problem, one step weaker.** A configuration change wipes anything held in a plain `remember`. Only ViewModels and repositories survive it.

The rule is this: **any state a composable owns outright is `rememberSaveable`, not `remember`.** This includes whether a sheet or dialog is open, unless a ViewModel owns it, as it does the report dialog. `remember` stays correct for state that is meaningless after recreation, for example an `Animatable` or a transient gesture offset.

**The back stack comes back. Nothing else does.** `rememberBackStack` saves the `@Serializable` entries, and `rememberSaveableStateHolderNavEntryDecorator` restores each entry's saveable state under that same identity. This is why the id must be a stored property, not a regenerated default.

Every repository, ViewModel, and store gets rebuilt from scratch. So **a screen must be able to rebuild everything it shows from its `Screen` key alone.** Anything that cannot be re-derived goes on the key: `AlbumViewer` carries its image URLs, and `FullscreenVideo` carries its URL and title.

For a page that fetches data, this means a **cold-start load**. Check whether the stores already hold what the key names, and fetch only when they do not, for example `EditProfileViewModel.load` and `PostDetailViewModel.loadPost` through `PostRepository.load`. A page opened the ordinary way skips the request.

**An absent entity is three states, not one**, on every screen that resolves one by id. Reading "the store has no such thing" as *not found* is correct only while the store is authoritative, and that stops being true the moment the app can start directly on that page.

`common/util/EntityFetch` holds the fetch's side of this: `Pending`, `Done` and `Failed`. The page reads the store first, and only an entity still absent after `Done` is `NotFound` (a 404, with nothing to retry); `Failed` is `Error` (retryable). `PostDetailViewModel` and `ProfileViewModel` both resolve through it. A retry resets it to `Pending`, so the page reads `Loading` again rather than a stale `NotFound`. `PostDataSource.fetch` returns `null` for a 404 through `notFoundAsNull`, instead of throwing.

**Deletion states its result explicitly**, because absence alone is ambiguous. `PostRepository.delete` records the ID in `deletedIds` before it drops the entity. So a detail page whose post was deleted from another screen reads `NotFound`, instead of spinning on a fetch that will never come.

`load` short-circuits on the same set, so "deleted" stays true even offline. Feed and profile need no such check. They resolve IDs through `entities` and drop whatever vanishes.

Both endpoints that answer with a *single* post embed the author. So `PostRepository` seeds `UserRepository` from `load` and from `create`. A sideloaded user belongs in the user store, no matter where it arrives from.

**What the user typed is the one thing that must be *saved*, not re-derived.** `CreatePostViewModel` and `EditProfileViewModel` persist their form through `SavedStateHandle`, scoped per back-stack entry.

`common/util/SavedDraft.kt` is the seam. `saveDraft` registers *where to read the draft from*, as a `() -> T`, not a `Flow<T>`, so the platform pulls the value at most once per save, and typing costs nothing. Serializing the whole form is what keeps `CreatePostMedia`'s variant, and with it the photos-or-video exclusivity, intact.

Only the *form* is saved. An in-flight publish and errors from a dead process are not saved, so a restored composer is idle. The editor re-reads its pristine snapshot from the server, so restored edits still read as unsaved.

**These tests are instrumented, on purpose.** A `SavedState` is a `Bundle`, so a JVM test could exercise only a stand-in.

`BackStackRestorationTest` walks one of every `Screen` through `emulateSavedInstanceStateRestore()`, which catches a key that quietly stops being serializable, and it pins entry identity across the restore. `DraftRestorationTest` does the round trip: save, fresh handle, rebuild.

ViewModel unit tests cover cold-start loading, since neither test type can emulate empty stores.

Video playback position is deliberately not restored.

## Compose performance

Strong skipping is on, through the built-in Compose compiler in Kotlin 2.4.20. This shapes what is worth doing:

- **Do not wrap lambdas in `remember` by reflex.** Strong skipping auto-remembers them, keyed on what they capture.
- **Make every type a composable takes provably stable.** Strong skipping still skips on an unstable parameter, but compares it by *instance*, so a new but equal value recomposes anyway. A stable parameter is compared with `equals`. The compiler states which is which in its stability reports (see *Commands*).
- **Run the stability reports after a new screen, and at the end of a large change.** CI does not run them. Read the reports in this order:
  1. In `*-composables.txt`, look for any parameter marked `unstable`. Each one is compared by instance.
  2. In `*-classes.txt`, look for `unstable` or `Uncertain(…)` on a type that a composable receives. The field lines under it name the cause. ViewModels, repositories, DTOs and data sources show as unstable too. Ignore them, because no composable takes them as a parameter.
  3. Fix a finding with the rules below, then run the reports again to confirm it is gone.

  The reports show only how a parameter is compared. They do not show state read too high in the tree, a lambda that captures a rebuilt wrapper, or work done on every frame. For those, read the code against the rest of this section, and measure on a device with Layout Inspector's recomposition counts.
- **Decide stability when you write the type, not later.** This applies to every new data class, sealed type, or state holder that a composable will receive:
  - A data class of `val`s with stable types needs nothing. The compiler infers it.
  - A sealed interface is *uncertain* to the compiler, even when every case is an immutable data class. Mark it `@Immutable`. `HomeUiState`, `PostDetailUiState.Content`, `CreatePostMedia`, `AppError` and `Screen` are examples.
  - A class whose state changes, but only through snapshot state, is `@Stable`, for example `VideoPlayback`.
  - `data/domain` may not import `androidx` (ArchitectureTest rule 3), so a domain model never carries an annotation. When one goes unstable, the cause is a field type from outside the project, and that type goes in `compose-stability.conf` at the repository root. `build-logic` hands it to every module. `java.time.Instant` and the read-only collections are there today.
  - **The annotation and the config entry are promises the compiler does not check.** A false one makes a composable skip a real change. Never apply either to a type with a `var`, or one that wraps a mutable object, for example `ClickDebounceGuard` or Media3's `Player`. Instance comparison is correct for those.
- **A lambda captures the stable values it needs, never a wrapper rebuilt on each emission.** Feed rows capture `postId` and `authorId`, not the `PostCardData` that `HomeViewModel` rebuilds for every post on every emission. The ids stay equal, so the lambdas stay the same instances.
- **Keep screen-wide state out of the rows.** When a state concerns one row at a time, such as the report dialog, draw it once at the screen level rather than threading it into every row: a card only raises `OpenReport`, so a report changing recomposes no card at all.
- **Skipping works by instance, so a mutation must preserve identity for everything unchanged.** `PostRepository` stores a `Map<PostId, Post>` and mutates it with `_entities.update { it + (postId to updated) }`, so a like recomposes exactly one `PostCard`. Mapping `copy()` over the whole collection would recompose every visible row. Avoid that.
- **Read snapshot state in the smallest composable that uses it.** Reading scroll or pager state at a screen's top level invalidates the whole scope. The album viewer keeps its page counter in a separate `PageIndicator`, so swiping recomposes only the pill.
- **Coalesce continuous signals with `derivedStateOf`**, for example a scroll offset into a boolean, so readers recompose only when the result flips. This is not needed for a signal that is already coalesced. `HomeScreen` reads `listState.canScrollBackward` directly.
- **Do not key a long-lived effect on state you only read inside it.** The feed's autoplay collector reads posts through `rememberUpdatedState`, and keys only on the stable `listState` and playback state. The auto-play *setting* is a key on purpose. The restart re-reads the current layout, so switching auto-play on plays the video already on screen.
- **Give lazy-list rows a `contentType` when their layouts differ.** A slot scrolled off screen is reused only by a row of the same type, so it keeps most of its composition. Post rows use `Post.cardContentType` (text, album or video) in the feed and on the profile. Rows of a single layout need none.
- **Keep work that runs on every frame cheap.** A `snapshotFlow` over `layoutInfo` or a scroll offset emits on every scroll frame. Loop by index with no temporary lists, as `mostVisibleVideoIndex` does, and call out to other objects only when the result changes.
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
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
)
```

**An expression body that produces a value goes on the line below the `=`**, indented once, however short it is. The declaration ends at the `=`. This way the signature and the value never compete for one line, annotations and a wrapped parameter list read down to the value instead of past it, and a body that grows later needs no re-wrap of the declaration above it. It applies to a constructor call, a builder, a constant, a chain — anything whose result *is* the return value.

```kotlin
// Yes
@Provides
@Singleton
fun provideJson(): Json =
    Json { ignoreUnknownKeys = true }

@Provides
fun providePostDataSource(api: PostApi): PostDataSource =
    NetworkPostDataSource(api)

// No
@Provides
@Singleton
fun provideJson(): Json = Json { ignoreUnknownKeys = true }
```

**A block of work is the exception: its brace stays on the declaration line.** Never break the line after the `=` there. Break *inside* the lambda instead.

```kotlin
// Yes
private fun toggleLike(postId: PostId) = launchCatching {
    postRepository.toggleLike(postId)
}

// No
private fun toggleLike(postId: PostId) =
    launchCatching { postRepository.toggleLike(postId) }
```

A **block of work**, for example anything launched, a coroutine body, or a test body, always takes its own lines. This way a ViewModel's handlers all read as one shape. A **small value expression** passed to such a block stays inline until it no longer fits on one line:

```kotlin
private fun setNickname(value: String) = updateForm { it.copy(nickname = value) }

private fun setAge(value: String) = updateForm { form ->
    form.copy(age = value.filter { it.isDigit() }.take(3))
}
```

**A trailing lambda is not what decides between the two rules — what the lambda *is* decides.** `Json { … }` builds a value, so it goes below the `=`. `launchCatching { … }` and `updateForm { … }` open a block of work, so they keep their brace up on the declaration line. When it is genuinely unclear, ask whether the lambda's last expression is the thing being returned.

**`onEvent` is a block of work too**, so its `when` opens on the declaration line: `fun onEvent(event: HomeUiEvent): Unit = when (event) {`, with each branch one indent in. **The declared `Unit` is load-bearing.** Without it, the return type is inferred from the branches, and one branch that returns a value would widen the public `onEvent` to `Any` with no warning. With it, that branch fails to compile.

**A screen's ViewModel and Screen files import its event and state types under a generic alias**: `import uno.lux.mosaic.home.ui.HomeUiEvent as UiEvent`, and `HomeUiState as UiState`. Every screen then reads the same way — `fun onEvent(event: UiEvent): Unit = when (event) {`, `onEvent: (UiEvent) -> Unit` — and the file name already says which screen it is. Every other file, tests included, keeps the full name, because it has no single screen for the alias to stand for.

**Both rules are about function bodies.** A property initializer is not one, and stays inline: `val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()`.

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

**The default is no KDoc.** Names, types and the body carry the meaning. Write one only for what a competent reader would get wrong without it:

- a contract the signature cannot show, such as what a `null` return means, or that a call is a no-op when repeated;
- a trap, where an obvious simplification would break something, such as initialization order, a library quirk, or a platform limitation;
- a value that mirrors the server, naming the server-side constant it must stay equal to.

Keep it to one or two sentences. If it needs a paragraph, the design belongs in this file and the KDoc does not.

Never write a KDoc that:

- **restates the name**, such as "Pops the top entry off the back stack", or "The report dialog";
- **describes the architecture**, such as which store a ViewModel reads from, or why a screen is stateless (this file says that once);
- **tells history**, such as "the previous version", "now", "used to", or the story of a past bug (that belongs in the commit message);
- **documents a test double or a test** that its name already explains, such as "Thrown by X instead of answering, so tests can drive the failure path";
- **describes a preview**, a color, a dimension or a duration by what it looks like.

## Localization

All user-facing text lives in a `res/values/strings.xml` and is read with `stringResource(...)`. **Never hardcode a display string in Kotlin.** Exception: a string with no words at all, for example `"$page / $total"`, is fine as plain interpolation.

Each module carries the strings its own code draws, so there are three `strings.xml` files and three `values-cs` counterparts. A string moves down to `:core:common` or `:core:design-system` the moment that module draws it, and `:app` keeps reading it — resources merge upward. Kotlin in `:app` that names a string owned by a library goes through `CommonR`, as *Package structure* describes.

Navigation labels are `@StringRes` IDs on `ShellDestinations`. Post content in `SampleData` is stand-in data, not app chrome, so it stays literal.

Computed text keeps its *logic* pure. `relativeTime()` and `compactCount()`, in `common/util`, return structured buckets with no display strings. `common/Formatting.kt`'s `asText()` resolves a bucket to a localized resource.

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
