package uno.lux.mosaic.settings.data.domain

/**
 * Deliberately has no "follow the system" entry; the device language only seeds the first choice.
 */
enum class AppLanguage(
    val languageTag: String,
) {
    ENGLISH("en"),
    CZECH("cs"),
    ;

    companion object {
        val Default: AppLanguage = ENGLISH

        /** Region subtags are ignored; `null` means the list named nothing the app ships. */
        fun fromLanguageTags(tags: String?): AppLanguage? =
            tags
                ?.split(',')
                ?.map { it.substringBefore('-').trim().lowercase() }
                ?.firstNotNullOfOrNull { tag -> entries.firstOrNull { it.languageTag == tag } }
    }
}
