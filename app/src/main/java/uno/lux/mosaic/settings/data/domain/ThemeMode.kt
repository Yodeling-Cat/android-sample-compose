package uno.lux.mosaic.settings.data.domain

/** The user's theme preference. [SYSTEM] follows the device's light/dark setting. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    /** Resolves whether dark colors should apply, given the current [systemInDark] state. */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDark
    }

    companion object {
        /** Parses a persisted [name] back to a mode, defaulting to [SYSTEM] when unknown or absent. */
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
