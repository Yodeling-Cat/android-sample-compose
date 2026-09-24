package uno.lux.mosaic.user.ui

import androidx.annotation.StringRes
import uno.lux.mosaic.R
import uno.lux.mosaic.user.data.domain.Gender

@get:StringRes
val Gender.labelRes: Int
    get() = when (this) {
        Gender.MAN -> R.string.gender_man
        Gender.WOMAN -> R.string.gender_woman
    }
