package uno.lux.mosaic.common.util

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Returns a no-op implementation of the interface [T], for Compose previews.
 *
 * The stateless composables take their callbacks bundled as a single `@Stable` *actions*
 * interface. A preview doesn't care what those callbacks do, so rather than hand-write an empty
 * override for each method, this returns a [Proxy] — backed by [ActionsInvocationHandler] — who's
 * every call is a no-op. So a preview reads simply:
 *
 * ```
 * HomeScreen(uiState = …, actions = createActionsProxy())
 * ```
 */
inline fun <reified T> createActionsProxy(): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf<Class<*>>(T::class.java),
        ActionsInvocationHandler(),
    ) as T

/**
 * The [InvocationHandler] behind [createActionsProxy]: every proxied *interface* call does nothing.
 *
 * `Object`'s three methods are answered properly rather than swallowed, because a proxy stands in
 * for a `@Stable` type and Compose takes that annotation at its word: a recomposition compares the
 * new actions against the slot's with `equals`, which returning [Unit] from answers with the wrong
 * type — `result has type boolean, got kotlin.Unit`. The proxy has no state to compare, so identity
 * is the honest answer, and [hashCode] has to agree with it.
 */
class ActionsInvocationHandler : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any =
        if (method.declaringClass != Any::class.java) {
            Unit
        } else {
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                else -> "ActionsProxy@${Integer.toHexString(System.identityHashCode(proxy))}"
            }
        }
}
