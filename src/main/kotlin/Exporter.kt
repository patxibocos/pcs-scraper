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
import org.jetbrains.exposed.sql.statements.InsertStatement
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

    fun exportTeams(teams: List<Team>)
    fun exportRiders(riders: List<Rider>)

    companion object {
        fun from(destinationPath: String, format: Format): Exporter {
            val destination = File(Paths.get(destinationPath).toUri()).also {
                it.mkdirs()
            }
            return when (format) {
                Format.JSON -> JsonExporter(destination)
                Format.SQLITE -> SQLiteExporter(destination.resolve("db.sqlite"))
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

    override fun exportRiders(riders: List<Rider>) {
        exportToJson(riders, "riders.json")
    }

    override fun exportTeams(teams: List<Team>) {
        exportToJson(teams, "teams.json")
    }

    private inline fun <reified T> exportToJson(data: T, fileName: String) {
        val serialized = json.encodeToString(data)
        this.destination.resolve(fileName).writeText(serialized)
    }
}

private class SQLiteExporter(override val destination: File) : Exporter {

    private fun <T> connectToDbAndInsert(table: DbTable<T>, data: List<T>) {
        Database.connect("jdbc:sqlite:${this.destination.absolutePath}", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(table)
            data.map { t: T ->
                table.insert {
                    table.fillInsertStatement(it, t)
                }
            }
        }
    }

    abstract class DbTable<T>(name: String) : Table(name = name) {
        abstract fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: T)
    }

    object DbTeam : DbTable<Team>(name = "team") {
        val id = text("id")
        val name = text("name")
        val status = text("status")
        val abbreviation = text("abbreviation")
        val country = text("country")
        val bike = text("bike")
        val jersey = text("jersey")
        val website = text("website").nullable()
        val year = integer("year")
        val riders = text("riders")

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Team) {
            insertStatement[id] = t.id
            insertStatement[name] = t.name
            insertStatement[status] = t.status.name
            insertStatement[abbreviation] = t.abbreviation
            insertStatement[country] = t.country
            insertStatement[bike] = t.bike
            insertStatement[jersey] = t.jersey.toString()
            insertStatement[website] = t.website
            insertStatement[year] = t.year
            insertStatement[riders] = Json.encodeToString(t.riders)
        }
    }

    object DbRider : DbTable<Rider>(name = "rider") {
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

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Rider) {
            insertStatement[id] = t.id
            insertStatement[firstName] = t.firstName
            insertStatement[lastName] = t.lastName
            insertStatement[country] = t.country
            insertStatement[website] = t.website
            insertStatement[birthDate] = t.birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[birthPlace] = t.birthPlace
            insertStatement[weight] = t.weight
            insertStatement[height] = t.height
            insertStatement[photo] = t.photo.toString()
        }
    }

    override fun exportTeams(teams: List<Team>) {
        connectToDbAndInsert(DbTeam, teams)
    }

    override fun exportRiders(riders: List<Rider>) {
        connectToDbAndInsert(DbRider, riders)
    }
}
