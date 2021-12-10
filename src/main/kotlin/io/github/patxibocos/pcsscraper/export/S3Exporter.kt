package io.github.patxibocos.pcsscraper.export

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.content.ByteStream
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildProtobufMessages
import io.github.patxibocos.pcsscraper.protobuf.cyclingData

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
        S3Client.fromEnvironment { region = "eu-west-3" }.use { s3Client ->
            s3Client.putObject {
                bucket = s3Bucket
                key = s3ObjectKey
                body = ByteStream.fromBytes(cyclingData.toByteArray())
                contentLength = cyclingData.serializedSize.toLong()
                contentType = "application/x-protobuf"
            }
        }
    }
}
