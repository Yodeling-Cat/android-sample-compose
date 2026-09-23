package uno.lux.mosaic.settings.data.domain

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDark
    }

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
