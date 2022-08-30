package io.github.patxibocos.pcsscraper.export

import com.google.protobuf.MessageLite
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildCyclingDataProtobuf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class ProtobufExporter(private val destination: File) : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val cyclingData = buildCyclingDataProtobuf(teams, riders, races)
        withContext(Dispatchers.IO) {
            exportProtobufMessage(cyclingData, "cycling.data")
        }
    }

    private fun exportProtobufMessage(protobufMessage: MessageLite, fileName: String) {
        val destinationFile = this@ProtobufExporter.destination.resolve(fileName)
        destinationFile.delete()
        protobufMessage.writeTo(destinationFile.outputStream())
    }
}
