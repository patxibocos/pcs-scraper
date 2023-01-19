package io.github.patxibocos.diffpublisher

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ParameterValue
import io.github.patxibocos.pcsscraper.protobuf.CyclingDataOuterClass
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

fun main() {
    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build()
    FirebaseApp.initializeApp(options)
    val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    var lastVersionNumber = ""
    var previousVersionNumber = ""
    firebaseRemoteConfig.listVersions().values.forEachIndexed { index, version ->
        when (index) {
            0 -> lastVersionNumber = version.versionNumber
            1 -> previousVersionNumber = version.versionNumber
        }
    }
    val lastVersionData =
        (firebaseRemoteConfig.getTemplateAtVersion(lastVersionNumber).parameters["cycling_data"]!!.defaultValue as ParameterValue.Explicit).value
    val previousVersionData =
        (firebaseRemoteConfig.getTemplateAtVersion(previousVersionNumber).parameters["cycling_data"]!!.defaultValue as ParameterValue.Explicit).value

    val lastCyclingData = CyclingDataOuterClass.CyclingData.parseFrom(decodeBase64ThenUnzip(lastVersionData))
    val previousCyclingData =
        CyclingDataOuterClass.CyclingData.parseFrom(decodeBase64ThenUnzip(previousVersionData))

    checkNewStagesWithResults(lastCyclingData, previousCyclingData)
}

private fun decodeBase64ThenUnzip(gzipBase64: String) =
    ByteArrayInputStream(Base64.getDecoder().decode(gzipBase64)).use { inputStream ->
        GZIPInputStream(
            inputStream,
        ).use { it.readBytes() }
    }

private fun checkNewStagesWithResults(
    lastData: CyclingDataOuterClass.CyclingData,
    previousData: CyclingDataOuterClass.CyclingData,
) {
    val logger: Logger = KotlinLogging.logger {}
    val lastResults =
        lastData.racesList.associate { race -> race.id to race.stagesList.associate { stage -> stage.id to stage.resultList } }
    val previousResults =
        previousData.racesList.associate { race -> race.id to race.stagesList.associate { stage -> stage.id to stage.resultList } }
    lastResults.forEach races@{ (raceId, stagesResults) ->
        val previousStagesResults = previousResults[raceId] ?: return@races
        stagesResults.forEach stages@{ (stageId, stageResults) ->
            val previousStageResults = previousStagesResults[stageId] ?: return@stages
            if (previousStageResults.isEmpty() && stageResults.isNotEmpty()) {
                logger.info("Results available for $raceId - $stageId")
                val message = Message.builder()
                    .setTopic("stage-results")
                    .putData("race-id", raceId)
                    .putData("stage-id", stageId)
                    .build()
                val firebaseMessaging = FirebaseMessaging.getInstance()
                firebaseMessaging.send(message)
            }
        }
    }
}
