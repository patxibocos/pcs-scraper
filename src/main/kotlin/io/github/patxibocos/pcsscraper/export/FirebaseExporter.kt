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
import io.github.patxibocos.pcsscraper.export.json.json
import kotlinx.serialization.encodeToString

internal class FirebaseExporter : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
        FirebaseApp.initializeApp(options)

        val teamsJson = json.encodeToString(teams)
        val ridersJson = json.encodeToString(riders)
        val racesJson = json.encodeToString(races)

        val template: Template = FirebaseRemoteConfig.getInstance().templateAsync.get()
        template.parameters["teams"] = Parameter().setDefaultValue(ParameterValue.of(teamsJson))
        template.parameters["riders"] = Parameter().setDefaultValue(ParameterValue.of(ridersJson))
        template.parameters["races"] = Parameter().setDefaultValue(ParameterValue.of(racesJson))
        FirebaseRemoteConfig.getInstance().publishTemplate(template)
    }
}
