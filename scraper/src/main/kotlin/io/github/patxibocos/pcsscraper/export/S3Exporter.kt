package io.github.patxibocos.pcsscraper.export

import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.protobuf.buildCyclingDataProtobuf

internal class S3Exporter : Exporter {

    override suspend fun export(teams: List<Team>, riders: List<Rider>, races: List<Race>) {
        val cyclingData = buildCyclingDataProtobuf(teams, riders, races)
        val s3Bucket = System.getenv("AWS_S3_BUCKET")
        val s3ObjectKey = System.getenv("AWS_S3_OBJECT_KEY")
        val s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_3).build()
        s3.putObject(
            s3Bucket,
            s3ObjectKey,
            cyclingData.toByteArray().inputStream(),
            ObjectMetadata().apply {
                contentType = "application/x-protobuf"
                contentLength = cyclingData.serializedSize.toLong()
            },
        )
    }
}
