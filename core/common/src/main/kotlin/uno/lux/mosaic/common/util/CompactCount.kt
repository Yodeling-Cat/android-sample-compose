package uno.lux.mosaic.common.util

/**
 * An engagement count split into its numeric [text] and scale, so the unit suffix (K/M)
 * comes from localized resources instead of being baked into code. Negative inputs are
 * coerced to zero.
 */
sealed interface CompactCount {
    val text: String

    data class Ones(
        override val text: String,
    ) : CompactCount

    data class Thousands(
        override val text: String,
    ) : CompactCount

    data class Millions(
        override val text: String,
    ) : CompactCount
}

fun compactCount(count: Int): CompactCount = when {
    count < 1_000 -> CompactCount.Ones(count.coerceAtLeast(0).toString())
    count < 1_000_000 -> CompactCount.Thousands(scaled(count, unit = 1_000))
    else -> CompactCount.Millions(scaled(count, unit = 1_000_000))
}

/** The count scaled to [unit] with at most one decimal place: 1_234 / 1_000 -> "1.2". */
private fun scaled(count: Int, unit: Int): String {
    val whole = count / unit
    val tenths = count % unit / (unit / 10)

    return if (tenths == 0) "$whole" else "$whole.$tenths"
}
