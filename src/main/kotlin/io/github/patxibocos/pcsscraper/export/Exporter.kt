package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import java.io.File
import java.nio.file.Paths

enum class Format {
    FIREBASE, JSON, PROTOBUF, S3, SQLITE
}

interface Exporter {

    suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>)

    companion object {
        fun from(destinationPath: String, format: Format): Exporter {
            val destination = File(Paths.get(destinationPath).toUri()).also { it.mkdirs() }
            return when (format) {
                Format.FIREBASE -> FirebaseExporter()
                Format.JSON -> JsonExporter(destination)
                Format.PROTOBUF -> ProtobufExporter(destination)
                Format.SQLITE -> SQLiteExporter(destination)
                Format.S3 -> S3Exporter()
            }
        }
    }
}
