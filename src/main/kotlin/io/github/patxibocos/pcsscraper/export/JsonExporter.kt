package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class JsonExporter(override val destination: File) : Exporter {
    private val json: Json = Json {
        serializersModule = SerializersModule {
            contextual(LocalDateSerializer)
            contextual(URLSerializer)
        }
    }

    private object LocalDateSerializer : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LocalDate) =
            encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))

        override fun deserialize(decoder: Decoder): LocalDate =
            LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private object URLSerializer : KSerializer<URL> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.net.URL", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: URL) =
            encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): URL =
            URL(decoder.decodeString())
    }

    override fun exportRiders(riders: List<Rider>) {
        exportToJson(riders, "riders.json")
    }

    override fun exportRacesWithStages(races: List<Race>) {
        exportToJson(races, "races.json")
    }

    override fun exportTeams(teams: List<Team>) {
        exportToJson(teams, "teams.json")
    }

    private inline fun <reified T> exportToJson(data: T, fileName: String) {
        val serialized = json.encodeToString(data)
        this.destination.resolve(fileName).writeText(serialized)
    }
}
