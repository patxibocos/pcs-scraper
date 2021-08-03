package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.format.DateTimeFormatter

internal class SQLiteExporter(override val destination: File) : Exporter {
    private fun <T> connectToDbAndInsert(table: DbTable<T>, data: List<T>) {
        Database.connect("jdbc:sqlite:${this.destination.absolutePath}", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(table)
            data.forEach { t: T ->
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

    object DbRace : DbTable<Race>(name = "race") {
        val id = text("id")
        val name = text("name")
        val startDate = text("start_date")
        val endDate = text("end_date")
        val website = text("website").nullable()

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id, name = "id")

        override fun fillInsertStatement(insertStatement: InsertStatement<Number>, t: Race) {
            insertStatement[id] = t.id
            insertStatement[name] = t.name
            insertStatement[startDate] = t.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[endDate] = t.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            insertStatement[website] = t.website
        }
    }

    override fun exportTeams(teams: List<Team>) {
        connectToDbAndInsert(DbTeam, teams)
    }

    override fun exportRiders(riders: List<Rider>) {
        connectToDbAndInsert(DbRider, riders)
    }

    override fun exportRaces(races: List<Race>) {
        connectToDbAndInsert(DbRace, races)
    }
}
