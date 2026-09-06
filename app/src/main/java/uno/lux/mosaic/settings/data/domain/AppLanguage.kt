package uno.lux.mosaic.settings.data.domain

/**
 * The languages the app ships translations for. There is deliberately no "follow the system" entry.
 * Instead, the system language seeds the choice once, on first launch — see
 * [uno.lux.mosaic.settings.data.AppLocaleRepository.resolveInitialLanguage].
 */
enum class AppLanguage(
    val languageTag: String,
) {
    ENGLISH("en"),
    CZECH("cs"),
    ;

    companion object {
        val Default: AppLanguage = ENGLISH

        /**
         * Returns the first language we ship. Region subtags are ignored, and `null` means
         * the list named nothing we ship.
         */
        fun fromLanguageTags(tags: String?): AppLanguage? =
            tags
                ?.split(',')
                ?.map { it.substringBefore('-').trim().lowercase() }
                ?.firstNotNullOfOrNull { tag -> entries.firstOrNull { it.languageTag == tag } }
    }
}
