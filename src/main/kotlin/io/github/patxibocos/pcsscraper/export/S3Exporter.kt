package io.github.patxibocos.pcsscraper.export

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildProtobufMessages
import io.github.patxibocos.pcsscraper.protobuf.cyclingData
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

internal class S3Exporter : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val (teamsMessages, ridersMessages, racesMessages) = buildProtobufMessages(teams, riders, races)
        val cyclingData = cyclingData {
            this.teams.addAll(teamsMessages)
            this.riders.addAll(ridersMessages)
            this.races.addAll(racesMessages)
        }
        val s3Bucket = System.getenv("AWS_S3_BUCKET")
        val s3ObjectKey = System.getenv("AWS_S3_OBJECT_KEY")
        S3Client.builder().region(Region.EU_WEST_3).build().use { s3Client ->
            s3Client.putObject(
                PutObjectRequest.builder().bucket(s3Bucket).key(s3ObjectKey)
                    .contentLength(cyclingData.serializedSize.toLong()).contentType("application/x-protobuf").build(),
                RequestBody.fromBytes(cyclingData.toByteArray())
            )
        }
    }
}
