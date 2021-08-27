package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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
) {
    @Serializable
    data class Stage(
        val id: String,
        @Contextual val startDate: LocalDate,
        val distance: Float,
        val type: Type?,
        val departure: String?,
        val arrival: String?,
        val result: List<RiderResult>,
        @kotlinx.serialization.Transient val raceId: String = "",
    ) {
        enum class Type {
            FLAT,
            HILLS_FLAT_FINISH,
            HILLS_UPHILL_FINISH,
            MOUNTAINS_FLAT_FINISH,
            MOUNTAINS_UPHILL_FINISH,
        }
    }

    @Serializable
    data class RiderResult(
        val position: Int,
        val rider: String,
        val time: Long,
        @kotlinx.serialization.Transient val stage: String = "",
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
        @kotlinx.serialization.Transient val race: String = "",
        @kotlinx.serialization.Transient val team: String = "",
    )
}
