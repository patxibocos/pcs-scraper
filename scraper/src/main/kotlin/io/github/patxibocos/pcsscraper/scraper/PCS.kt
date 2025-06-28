package io.github.patxibocos.pcsscraper.scraper

import kotlin.math.max

const val PCS_URL = "https://www.procyclingstats.com"

data class PCSTeam(
    val url: String,
    val name: String,
    val status: String,
    val abbreviation: String,
    val country: String,
    val bike: String,
    val jersey: String,
    val website: String?,
    val year: Int,
    val riders: List<String>,
)

data class PCSRider(
    val url: String,
    val fullName: String,
    val country: String,
    val website: String? = null,
    val birthDate: String,
    val birthPlace: String? = null,
    val weight: String? = null,
    val height: String? = null,
    val photo: String,
    val uciRankingPosition: String?,
) {
    fun getFirstAndLastName(fullName: String): Pair<String, String> {
        val index = max(fullName.indexOfFirst { it.isLowerCase() } - 2, fullName.indexOfFirst { it.isWhitespace() })
        val firstName = fullName.substring(index + 1, fullName.length)
        val lastName = fullName.substring(0, index).split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() }.trim() }
        return firstName to lastName
    }
}

data class PCSRace(
    val url: String,
    val name: String,
    val country: String,
    val website: String?,
    val stages: List<PCSStage>,
    val startList: List<PCSTeamParticipation>,
)

data class PCSTeamParticipation(
    val team: String,
    val riders: List<PCSRiderParticipation>,
)

data class PCSRiderParticipation(
    val rider: String,
    val number: String,
)

data class PCSStage(
    val url: String,
    val startDate: String,
    val startTimeCET: String?,
    val distance: String,
    val type: String,
    val individualTimeTrial: Boolean,
    val teamTimeTrial: Boolean,
    val departure: String?,
    val arrival: String?,
    val stageTimeResult: List<PCSParticipantResult>,
    val stageYouthResult: List<PCSParticipantResult>,
    val stageTeamsResult: List<PCSParticipantResult>,
    val stageKomResult: List<PCSPlaceResult>,
    val stagePointsResult: List<PCSPlaceResult>,
    val generalTimeResult: List<PCSParticipantResult>,
    val generalYouthResult: List<PCSParticipantResult>,
    val generalTeamsResult: List<PCSParticipantResult>,
    val generalKomResult: List<PCSParticipantResult>,
    val generalPointsResult: List<PCSParticipantResult>,
)

data class PCSParticipantResult(
    val position: String,
    val participant: String,
    val result: String,
    val name: String,
)

data class PCSPlaceResult(
    val place: PointsPlace,
    val result: List<PCSParticipantResult>,
)

data class PointsPlace(
    val title: String,
)
