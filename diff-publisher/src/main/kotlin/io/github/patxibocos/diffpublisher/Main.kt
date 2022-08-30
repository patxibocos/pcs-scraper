package io.github.patxibocos.diffpublisher

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ParameterValue
import io.github.patxibocos.pcsscraper.protobuf.CyclingDataOuterClass
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

fun main() {
    val logger: Logger = KotlinLogging.logger {}
    logger.info("POLLA")
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
}

private fun decodeBase64ThenUnzip(gzipBase64: String) =
    ByteArrayInputStream(Base64.getDecoder().decode(gzipBase64)).use { inputStream ->
        GZIPInputStream(
            inputStream
        ).use { it.readBytes() }
    }
