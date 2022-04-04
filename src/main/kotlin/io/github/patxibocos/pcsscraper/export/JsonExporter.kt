package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

internal class JsonExporter(private val destination: File) : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        exportToJson(riders, "riders.json")
        exportToJson(teams, "teams.json")
        exportToJson(races, "races.json")
    }

    private suspend inline fun <reified T> exportToJson(data: T, fileName: String) {
        val serialized = withContext(Dispatchers.Default) {
            json.encodeToString(data)
        }
        val destinationFile = this@JsonExporter.destination.resolve(fileName)
        withContext(Dispatchers.IO) {
            destinationFile.delete()
            destinationFile.writeText(serialized)
        }
    }
}
