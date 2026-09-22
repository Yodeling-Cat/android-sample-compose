package uno.lux.mosaic.common.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwraps the [Activity] backing a [Context], walking [ContextWrapper]s. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
