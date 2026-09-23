package uno.lux.mosaic.settings.data.domain

const val DEFAULT_AUTO_PLAY_VIDEOS = false

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoPlayVideos: Boolean = DEFAULT_AUTO_PLAY_VIDEOS,
    /**
     * `null` until a language has been chosen. Keep it nullable: a first launch resolves exactly
     * that.
     */
    val language: AppLanguage? = null,
)
