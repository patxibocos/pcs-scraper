package io.github.patxibocos.pcsscraper.export

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import com.google.protobuf.MessageLite
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildCyclingDataProtobuf
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPOutputStream

internal class FirebaseExporter : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
        FirebaseApp.initializeApp(options)

        val cyclingData = buildCyclingDataProtobuf(teams, riders, races)

        val cyclingDataGzipBase64 = gzipThenBase64(cyclingData)

        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        retryForFirebaseException {
            val template = firebaseRemoteConfig.template
            template.parameters["cycling_data"] =
                Parameter().setDefaultValue(ParameterValue.of(cyclingDataGzipBase64))
            firebaseRemoteConfig.publishTemplate(template)
        }
    }

    private fun gzipThenBase64(message: MessageLite): String =
        ByteArrayOutputStream(message.toByteArray().size).use { originalStream ->
            GZIPOutputStream(originalStream).use {
                it.write(message.toByteArray())
                originalStream
            }
        }.toByteArray().let(Base64.getEncoder()::encodeToString)

    private fun <T> retryForFirebaseException(retries: Int = 3, f: () -> T): T {
        return try {
            f()
        } catch (e: FirebaseRemoteConfigException) {
            if (retries == 0) {
                throw e
            }
            retryForFirebaseException(retries - 1, f)
        }
    }
}
