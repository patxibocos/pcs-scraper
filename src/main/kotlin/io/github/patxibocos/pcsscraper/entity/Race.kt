package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Race(
    val id: String,
    val name: String,
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
        val departure: String?,
        val arrival: String?,
        @kotlinx.serialization.Transient val raceId: String = "",
    )

    @Serializable
    data class TeamParticipation(
        val teamId: String,
        val riders: List<String>,
        @kotlinx.serialization.Transient val raceId: String = "",
    )
}
