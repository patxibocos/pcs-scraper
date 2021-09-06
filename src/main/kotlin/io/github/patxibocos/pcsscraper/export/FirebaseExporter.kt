package io.github.patxibocos.pcsscraper.export

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import com.google.firebase.remoteconfig.Template
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildProtobufMessages
import kotlinx.serialization.ExperimentalSerializationApi
import java.util.Base64

internal class FirebaseExporter : Exporter {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
        FirebaseApp.initializeApp(options)

        val (teamsProtobufMessage, ridersProtobufMessage, racesProtobufMessage) = buildProtobufMessages(
            teams,
            riders,
            races
        )
        val teamsBase64 = Base64.getEncoder().encodeToString(teamsProtobufMessage.toByteArray())
        val ridersBase64 = Base64.getEncoder().encodeToString(ridersProtobufMessage.toByteArray())
        val racesBase64 = Base64.getEncoder().encodeToString(racesProtobufMessage.toByteArray())

        val template: Template = FirebaseRemoteConfig.getInstance().templateAsync.get()
        template.parameters["teams"] = Parameter().setDefaultValue(ParameterValue.of(teamsBase64))
        template.parameters["riders"] = Parameter().setDefaultValue(ParameterValue.of(ridersBase64))
        template.parameters["races"] = Parameter().setDefaultValue(ParameterValue.of(racesBase64))
        FirebaseRemoteConfig.getInstance().publishTemplate(template)
    }
}
