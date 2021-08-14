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
    data class TeamParticipation(
        val team: String,
        val riders: List<String>,
        @kotlinx.serialization.Transient val raceId: String = "",
    )
}
