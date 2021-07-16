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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Format {
    JSON, SQLITE
}

interface Exporter {
    val destination: File

    fun export(teamsAndRiders: TeamsAndRiders)

    companion object {
        fun from(output: String, format: Format): Exporter {
            val destination = File(Paths.get(output).toUri()).also {
                it.parentFile.mkdirs()
                it.delete()
            }
            return when (format) {
                Format.JSON -> JsonExporter(destination)
                Format.SQLITE -> SQLiteExporter(destination)
            }
        }
    }
}

private class JsonExporter(override val destination: File) : Exporter {

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

    override fun export(teamsAndRiders: TeamsAndRiders) {
        val serialized = json.encodeToString(teamsAndRiders)
        this.destination.writeText(serialized)
    }

}

private class SQLiteExporter(override val destination: File) : Exporter {

    object DbTeam : Table(name = "team") {
        val id = text("id")
        val name = text("name")
        val status = text("status")
        val abbreviation = text("abbreviation")
        val country = text("country")
        val bike = text("bike")
        val jersey = text("jersey")
        val website = text("website").nullable()
        val year = integer("year")

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")
    }

    object DbRider : Table(name = "rider") {
        val id = text("id")
        val firstName = text("first_name")
        val lastName = text("last_name")
        val country = text("country")
        val website = text("website").nullable()
        val birthDate = text("birth_date")
        val birthPlace = text("birth_place").nullable()
        val weight = integer("weight").nullable()
        val height = integer("height").nullable()
        val photo = text("photo")
        val team = text("team_id") references DbTeam.id

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")
    }

    override fun export(teamsAndRiders: TeamsAndRiders) {
        Database.connect("jdbc:sqlite:${this.destination.absolutePath}", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(DbTeam, DbRider)
            teamsAndRiders.teams.map { team ->
                DbTeam.insert {
                    it[id] = team.id
                    it[name] = team.name
                    it[status] = team.status.name
                    it[abbreviation] = team.abbreviation
                    it[country] = team.country
                    it[bike] = team.bike
                    it[jersey] = team.jersey.toString()
                    it[website] = team.website
                    it[year] = team.year
                }
                team.riders.map { rider ->
                    DbRider.insert {
                        it[id] = rider.id
                        it[firstName] = rider.firstName
                        it[lastName] = rider.lastName
                        it[country] = rider.country
                        it[website] = rider.website
                        it[birthDate] = rider.birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        it[birthPlace] = rider.birthPlace
                        it[weight] = rider.weight
                        it[height] = rider.height
                        it[photo] = rider.photo.toString()
                        it[DbRider.team] = team.id
                    }
                }
            }
        }
    }

}