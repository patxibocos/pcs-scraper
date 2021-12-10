package io.github.patxibocos.pcsscraper.export

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import com.google.firebase.remoteconfig.Template
import com.google.protobuf.MessageLite
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildProtobufMessages
import io.github.patxibocos.pcsscraper.protobuf.races
import io.github.patxibocos.pcsscraper.protobuf.riders
import io.github.patxibocos.pcsscraper.protobuf.teams
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

internal class FirebaseExporter : Exporter {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
        FirebaseApp.initializeApp(options)

        val (teamsMessages, ridersMessages, racesMessages) = buildProtobufMessages(
            teams,
            riders,
            races
        )

        val teamsGzipBase64 = gzipThenBase64(teams { this.teams.addAll(teamsMessages) })
        val ridersGzipBase64 = gzipThenBase64(riders { this.riders.addAll(ridersMessages) })
        val racesGzipBase64 = gzipThenBase64(races { this.races.addAll(racesMessages) })

        val template: Template = FirebaseRemoteConfig.getInstance().templateAsync.get()
        template.parameters["teams"] = Parameter().setDefaultValue(ParameterValue.of(teamsGzipBase64))
        template.parameters["riders"] = Parameter().setDefaultValue(ParameterValue.of(ridersGzipBase64))
        template.parameters["races"] = Parameter().setDefaultValue(ParameterValue.of(racesGzipBase64))
        FirebaseRemoteConfig.getInstance().publishTemplate(template)
    }

    private fun gzipThenBase64(message: MessageLite): String =
        ByteArrayOutputStream(message.toByteArray().size).use { originalStream ->
            GZIPOutputStream(originalStream).use {
                it.write(message.toByteArray())
                originalStream
            }
        }.toByteArray().let(Base64.getEncoder()::encodeToString)
}
