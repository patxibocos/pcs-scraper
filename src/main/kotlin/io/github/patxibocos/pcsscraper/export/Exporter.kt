package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import java.io.File
import java.nio.file.Paths

enum class Format {
    JSON, SQLITE
}

interface Exporter {
    val destination: File

    fun exportTeams(teams: List<Team>)
    fun exportRiders(riders: List<Rider>)
    fun exportRacesWithStages(races: List<Race>)

    companion object {
        fun from(destinationPath: String, format: Format): Exporter {
            val destination = File(Paths.get(destinationPath).toUri()).also { it.mkdirs() }
            return when (format) {
                Format.JSON -> JsonExporter(destination)
                Format.SQLITE -> SQLiteExporter(destination)
            }
        }
    }
}
