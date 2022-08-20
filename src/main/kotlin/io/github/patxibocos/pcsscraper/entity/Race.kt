package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class Race(
    val id: String,
    val name: String,
    val country: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val website: String?,
    val stages: List<Stage>,
    val startList: List<TeamParticipation>,
    val result: List<ParticipantResult>,
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
        val result: List<ParticipantResult>,
        val gcResult: List<ParticipantResult>,
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
    data class ParticipantResult(
        val position: Int,
        val participant: String,
        val time: Long,
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
