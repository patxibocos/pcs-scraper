package io.github.patxibocos.pcsscraper.export

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildCyclingDataProtobuf

internal class S3Exporter : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val cyclingData = buildCyclingDataProtobuf(teams, riders, races)
        val s3Bucket = System.getenv("AWS_S3_BUCKET")
        val s3ObjectKey = System.getenv("AWS_S3_OBJECT_KEY")
        val request = PutObjectRequest {
            bucket = s3Bucket
            key = s3ObjectKey
            body = ByteStream.fromBytes(cyclingData.toByteArray())
            contentType = "application/x-protobuf"
            contentLength = cyclingData.serializedSize.toLong()
        }
        S3Client { region = "eu-west-3" }.use { s3 ->
            s3.putObject(request)
        }
    }
}
