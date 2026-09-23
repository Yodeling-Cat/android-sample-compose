package uno.lux.mosaic.common.util

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

private fun scaled(count: Int, unit: Int): String {
    val whole = count / unit
    val tenths = count % unit / (unit / 10)

    return if (tenths == 0) "$whole" else "$whole.$tenths"
}
