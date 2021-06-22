import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.LocalDate
import java.util.*

class GetTeamsAndRiders(private val pcsParser: PCSParser) {

    operator fun invoke(season: Int): TeamsAndRiders {
        val pcsTeams = pcsParser.getTeamsUrls(season).map(pcsParser::getTeam)
        val pcsRiders = pcsTeams.flatMap(PCSTeam::riders)
            .map { (riderUrl, riderFullName) -> pcsParser.getRider(riderUrl, riderFullName) }
        val teams = pcsTeams.map(pcsParser::pcsTeamToTeam)
        val riders = pcsRiders.map(pcsParser::pcsRiderToRider)
        return TeamsAndRiders(
            season = season,
            teams = teams,
            riders = riders,
        )
    }

}

@Serializable
data class TeamsAndRiders(
    val season: Int,
    val teams: List<Team>,
    val riders: List<Rider>
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val country: String,
    val bike: String,
    @Contextual val jersey: URL,
    val website: String?,
    val year: Int,
    val riders: List<String>,
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