package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.format.DateTimeFormatter

internal class SQLiteExporter(destination: File) : Exporter {

    private val destinationFile = destination.resolve("db.sqlite").also { it.delete() }

    private fun <T> connectToDbAndInsert(
        table: DbTable<T>,
        data: List<T>,
        enricher: (InsertStatement<Number>) -> Unit = {}
    ) {
        Database.connect("jdbc:sqlite:${destinationFile.absolutePath}", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(table)
            data.forEach { t: T ->
                table.insert {
                    enricher(it)
                    table.fillInsertStatement(it, t)
                }
            }
        }
    }

    private abstract class DbTable<T>(name: String) : Table(name = name) {
        abstract fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: T)
    }

    private object DbTeam : DbTable<Team>(name = "team") {
        val id = text("id")
        private val name = text("name")
        private val status = text("status")
        private val abbreviation = text("abbreviation")
        private val country = text("country")
        private val bike = text("bike")
        private val jersey = text("jersey")
        private val website = text("website").nullable()
        private val year = integer("year")
        private val riders = text("riders")

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        @OptIn(ExperimentalSerializationApi::class)
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

    private object DbRider : DbTable<Rider>(name = "rider") {
        val id = text("id")
        private val firstName = text("first_name")
        private val lastName = text("last_name")
        private val country = text("country")
        private val website = text("website").nullable()
        private val birthDate = text("birth_date")
        private val birthPlace = text("birth_place").nullable()
        private val weight = integer("weight").nullable()
        private val height = integer("height").nullable()
        private val photo = text("photo")

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

    private object DbRace : DbTable<Race>(name = "race") {
        val id = text("id")
        private val name = text("name")
        private val country = text("country")
        private val startDate = text("start_date")
        private val endDate = text("end_date")
        private val website = text("website").nullable()

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race) {
            insertStatement[id] = t.id
            insertStatement[name] = t.name
            insertStatement[country] = t.country
            insertStatement[startDate] = t.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[endDate] = t.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[website] = t.website
        }
    }

    private object DbRaceRiderResult :
        SQLiteExporter.DbTable<Race.RiderResult>(name = "race_rider_result") {
        val raceId = text("race_id") references DbRace.id
        private val riderId = text("rider_id") references DbRider.id
        private val position = integer("position")
        private val time = long("time")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race.RiderResult) {
            insertStatement[riderId] = t.rider
            insertStatement[position] = t.position
            insertStatement[time] = t.time
        }
    }

    private object DbStage : DbTable<Race.Stage>(name = "stage") {
        val id = text("id")
        private val startDate = text("start_date")
        private val distance = float("distance")
        private val type = text("type").nullable()
        private val departure = text("departure").nullable()
        private val arrival = text("arrival").nullable()
        val raceId = text("race_id") references DbRace.id

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race.Stage) {
            insertStatement[id] = t.id
            insertStatement[startDate] = t.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[distance] = t.distance
            insertStatement[type] = t.type?.name
            insertStatement[departure] = t.departure
            insertStatement[arrival] = t.arrival
        }
    }

    private object DbRiderParticipation :
        SQLiteExporter.DbTable<Race.RiderParticipation>(name = "rider_participation") {
        val raceId = text("race_id") references DbRace.id
        val teamId = text("team_id") references DbTeam.id
        private val riderId = text("rider_id") references DbRider.id
        private val number = integer("number").nullable()

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race.RiderParticipation) {
            insertStatement[riderId] = t.rider
            insertStatement[number] = t.number
        }
    }

    private object DbStageRiderResult :
        SQLiteExporter.DbTable<Race.RiderResult>(name = "stage_rider_result") {
        val stageId = text("stage_id") references DbStage.id
        private val riderId = text("rider_id") references DbRider.id
        private val position = integer("position")
        private val time = long("time")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race.RiderResult) {
            insertStatement[riderId] = t.rider
            insertStatement[position] = t.position
            insertStatement[time] = t.time
        }
    }

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        connectToDbAndInsert(DbRider, riders)
        connectToDbAndInsert(DbTeam, teams)
        connectToDbAndInsert(DbRace, races)
        races.forEach { race ->
            connectToDbAndInsert(DbRaceRiderResult, race.result) {
                it[DbRaceRiderResult.raceId] = race.id
            }
            connectToDbAndInsert(DbStage, race.stages) {
                it[DbStage.raceId] = race.id
            }
            race.startList.forEach { team ->
                connectToDbAndInsert(DbRiderParticipation, team.riders) {
                    it[DbRiderParticipation.raceId] = race.id
                    it[DbRiderParticipation.teamId] = team.team
                }
            }
            race.stages.forEach { stage ->
                connectToDbAndInsert(DbStageRiderResult, race.result) {
                    it[DbStageRiderResult.stageId] = stage.id
                }
            }
        }
    }
}
