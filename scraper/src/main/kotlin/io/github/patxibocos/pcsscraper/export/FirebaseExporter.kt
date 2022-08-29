package io.github.patxibocos.pcsscraper.export

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import com.google.firebase.remoteconfig.Template
import com.google.protobuf.MessageLite
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildCyclingDataProtobuf
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

internal class FirebaseExporter(private val logger: Logger = KotlinLogging.logger {}) : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
        FirebaseApp.initializeApp(options)

        val cyclingData = buildCyclingDataProtobuf(teams, riders, races)

        val cyclingDataGzipBase64 = gzipThenBase64(cyclingData)

        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val templateBeforeUpdate: Template = firebaseRemoteConfig.template
        templateBeforeUpdate.parameters["cycling_data"] =
            Parameter().setDefaultValue(ParameterValue.of(cyclingDataGzipBase64))
        try {
            firebaseRemoteConfig.publishTemplate(templateBeforeUpdate)
        } catch (e: FirebaseRemoteConfigException) {
            logger.error("Firebase threw an exception", e)
            val templateAfterUpdate = firebaseRemoteConfig.template
            val previousVersionNumber = templateBeforeUpdate.version.versionNumber
            val currentVersionNumber = templateAfterUpdate.version.versionNumber
            if (templateBeforeUpdate.version == templateAfterUpdate.version) {
                logger.error("Version hasn't increased")
                throw e
            }
            logger.info("Previous version was $previousVersionNumber and the current is $currentVersionNumber")
        }
    }

    private fun gzipThenBase64(message: MessageLite): String =
        ByteArrayOutputStream(message.toByteArray().size).use { originalStream ->
            GZIPOutputStream(originalStream).use {
                it.write(message.toByteArray())
                originalStream
            }
        }.toByteArray().let(Base64.getEncoder()::encodeToString)
}
