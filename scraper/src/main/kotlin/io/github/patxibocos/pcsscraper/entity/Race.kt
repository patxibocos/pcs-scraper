package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Race(
    val id: String,
    val name: String,
    val country: String,
    val website: String?,
    val stages: List<Stage>,
    val startList: List<TeamParticipation>,
) {
    @Serializable
    data class Stage(
        val id: String,
        @Contextual val startDateTime: Instant,
        val distance: Float,
        val profileType: ProfileType?,
        val departure: String?,
        val arrival: String?,
        val stageType: StageType,
        val stageResults: StageResults,
        val generalResults: GeneralResults,
    ) {
        enum class ProfileType {
            FLAT,
            HILLS_FLAT_FINISH,
            HILLS_UPHILL_FINISH,
            MOUNTAINS_FLAT_FINISH,
            MOUNTAINS_UPHILL_FINISH,
        }

        enum class StageType {
            REGULAR,
            INDIVIDUAL_TIME_TRIAL,
            TEAM_TIME_TRIAL,
        }
    }

    @Serializable
    data class StageResults(
        val time: List<ParticipantResultTime>,
        val youth: List<ParticipantResultTime>,
        val teams: List<ParticipantResultTime>,
        val kom: List<PlaceResult>,
        val points: List<PlaceResult>,
    )

    @Serializable
    data class GeneralResults(
        val time: List<ParticipantResultTime>,
        val youth: List<ParticipantResultTime>,
        val teams: List<ParticipantResultTime>,
        val kom: List<ParticipantResultPoints>,
        val points: List<ParticipantResultPoints>,
    )

    @Serializable
    data class ParticipantResultTime(
        val position: Int,
        val participant: String,
        val time: Long,
    )

    @Serializable
    data class ParticipantResultPoints(
        val position: Int,
        val participant: String,
        val points: Int,
    )

    @Serializable
    data class PlaceResult(
        val place: Place,
        val points: List<ParticipantResultPoints>,
    )

    @Serializable
    data class Place(
        val name: String,
        val distance: Float,
    )

    @Serializable
    data class TeamParticipation(
        val team: String,
        val riders: List<RiderParticipation>,
    )

    @Serializable
    data class RiderParticipation(
        val rider: String,
        val number: Int?,
    )
}
