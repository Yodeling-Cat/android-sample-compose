# Assisted injection

**Assisted injection = the DI container builds an object that needs *some* dependencies from the graph and *some* values only known at runtime.**

## The problem it solves

Normal DI works when every constructor parameter is something the container knows how to build:

```kotlin
class PostRepository(
    private val dataSource: PostDataSource,     // container knows this
    private val userRepository: UserRepository, // and this
)
```

The container walks the graph, finds a binding for each type, and constructs it. You never write `PostRepository(...)` by hand.

Now consider a ViewModel for a profile page:

```kotlin
class ProfileViewModel(
    private val userId: UserId,                 // ← only known when the page opens
    private val postRepository: PostRepository, // container knows this
    private val navigator: Navigator,           // and this
)
```

`userId` is not a *dependency*. There is no single `UserId` in the app to bind — it's a different value per screen, chosen at the moment of navigation. The container cannot supply it.

## The three bad options without assisted injection

1. **Bind the runtime value into the graph.** Add a `@Provides fun userId(): UserId`. Now you need a scope per screen, and something has to mutate that binding before construction. This is how people end up with a mutable "current user id" holder that leaks across pages.

2. **Construct everything by hand.** `ProfileViewModel(userId, postRepository, navigator)` at the call site — now the call site needs to know about `PostRepository` and `Navigator`, which is exactly what DI was for. Add a fourth dependency and every call site changes.

3. **Pass it in after construction.** `viewModel.userId = userId` or an `init(userId)` method. Now the field is `lateinit`/nullable, and the object has a window where it's half-built. Every read has to tolerate "not set yet."

## What assisted injection does

You mark which parameters are *assisted* — supplied by the caller — and let the container fill the rest. The container generates a **factory**: an interface whose method takes only the assisted parameters and returns the fully-built object.

This is the real `ProfileViewModel` in this project, trimmed to the constructor:

```kotlin
@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    private val profileRepository: ProfileRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val navigator: Navigator,
    @param:CurrentUserId private val currentUserId: UserId,
    @Assisted private val userId: UserId,
) : ViewModel(), ProfileActions {

    @AssistedFactory
    interface Factory {
        fun create(userId: UserId): ProfileViewModel
    }
}
```

Note the two `UserId` parameters. `currentUserId` is qualified with `@CurrentUserId` and comes from the graph; `userId` is `@Assisted` and comes from the caller. Same type, opposite origins — the annotations are what keep them apart.

The generated factory implementation holds `Provider<ProfileRepository>`, `Provider<Navigator>`, and so on, pulled from the graph at *its* construction time. Its `create(userId)` just calls the real constructor with both halves.

The call site injects `ProfileViewModel.Factory` — one type — and calls `create(userId)`. When you add a sixth graph dependency, the factory interface doesn't change, so no call site changes.

## Why the factory is the whole point

The factory is the seam that keeps the two kinds of parameter separate:

- **Graph side**: the factory itself is injectable, so whoever holds it doesn't know or care what's behind it.
- **Runtime side**: the factory method signature is the *contract* for what the caller must supply. It's compile-checked. Forget the `userId` and it doesn't build.

Without the factory you'd have to choose: either the call site knows everything, or the container knows everything. The factory lets each side know only its half.

## Where it shows up in this codebase

`ProfileViewModel` and `EditProfileViewModel` use it — `userId` comes from the `Screen.Profile(userId)` navigation key, and the repositories come from Hilt. On Android, Hilt has a purpose-built path for this: `@HiltViewModel(assistedFactory = …)` plus `@AssistedInject`, with this at the composable:

```kotlin
viewModel: ProfileViewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
    creationCallback = { factory -> factory.create(userId) },
),
```

The generated `ViewModelProvider.Factory` bridges the assisted factory into the ViewModel store, so the instance is still scoped to the back-stack entry, survives configuration change, and is cleared when the page pops.

Contrast with `@CurrentUserId` in [CurrentUserId.kt](../app/src/main/java/uno/lux/mosaic/app/di/CurrentUserId.kt): that *is* a graph binding, because there is exactly one signed-in user for the app's lifetime. A value belongs in the graph when the container can answer "which one?" without being told. `userId` on a profile page fails that test; the signed-in user passes it.

## The general shape (beyond Android)

Assisted injection exists in Dagger, Hilt, Guice (`FactoryModuleBuilder`), and Kotlin-Inject. The pattern is the same everywhere and it's not really about frameworks — it's the observation that objects have two kinds of constructor parameter:

- **Dependencies** — "what do I need to do my job," answered once, globally.
- **Configuration** — "what am I doing this job *for*," answered per instance.

Assisted injection is the machinery for letting one construction site satisfy both without the two leaking into each other.

A useful test: **if you can't answer "which instance?" without knowing the call site, it's assisted.**
