package uno.lux.mosaic.common.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Serializes an [Instant] as its ISO-8601 string — the wire format the backend sends timestamps in.
 *
 * Applied with `@Serializable(with = InstantSerializer::class)` so timestamp fields on the DTOs are
 * typed as [Instant] directly: the parse happens during JSON decoding, and the DTO → domain mappers
 * need no timestamp conversion of their own.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
