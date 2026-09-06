package uno.lux.mosaic.settings.data.domain

const val DEFAULT_AUTO_PLAY_VIDEOS = false

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Whether the feed starts a video on its own as it scrolls into view. */
    val autoPlayVideos: Boolean = DEFAULT_AUTO_PLAY_VIDEOS,
    /** `null` until a language has been chosen, which is what a first launch resolves. */
    val language: AppLanguage? = null,
)
