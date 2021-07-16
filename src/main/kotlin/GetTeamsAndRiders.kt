import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.LocalDate
import java.util.*

class GetTeamsAndRiders(private val pcsParser: PCSParser) {

    operator fun invoke(season: Int): TeamsAndRiders {
        val pcsTeams = pcsParser.getTeamsUrls(season).map(pcsParser::getTeam)
        val teams = pcsTeams.map { pcsTeam ->
            val teamRiders =
                pcsTeam.riders.map { (riderUrl, riderFullName) -> pcsParser.getRider(riderUrl, riderFullName) }
                    .map(pcsParser::pcsRiderToRider)
            pcsParser.pcsTeamToTeam(pcsTeam, teamRiders)
        }.sortedBy { it.name }

        return TeamsAndRiders(
            season = season,
            teams = teams,
        )
    }

}

@Serializable
data class TeamsAndRiders(
    val season: Int,
    val teams: List<Team>,
)

enum class TeamStatus {
    WT, PRT
}

@Serializable
data class Team(
    val id: String,
    val name: String,
    val status: TeamStatus,
    val abbreviation: String,
    val country: String,
    val bike: String,
    @Contextual val jersey: URL,
    val website: String?,
    val year: Int,
    val riders: List<Rider>,
) {
    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}

@Serializable
data class Rider(
    val id: String,
    val firstName: String,
    val lastName: String,
    val country: String,
    val website: String?,
    @Contextual val birthDate: LocalDate,
    val birthPlace: String?,
    val weight: Int?,
    val height: Int?,
    @Contextual val photo: URL,
) {
    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}