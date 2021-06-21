import java.time.LocalDate
import java.util.*

class GetTeamsAndRiders(private val docFetcher: DocFetcher, private val pcsParser: PCSParser) {

    operator fun invoke(season: Int): TeamsAndRiders {
        val teamsDoc = docFetcher.getDoc("teams.php?year=$season&filter=Filter&s=worldtour")
        val pcsTeams = pcsParser.parseTeamsDoc(teamsDoc).map { teamUrl ->
            docFetcher.getDoc(teamUrl) { relaxed = true }
        }.map(pcsParser::parseTeamDoc)
        val pcsRiders = pcsTeams.flatMap(PCSParser.PCSTeam::riders).map { (riderUrl, riderFullName) ->
            val riderDoc = docFetcher.getDoc(riderUrl) { relaxed = true }
            pcsParser.parseRiderDoc(riderDoc, riderFullName)
        }
        val teams = pcsTeams.map(pcsParser::pcsTeamToTeam)
        val riders = pcsRiders.map(pcsParser::pcsRiderToRider)
        return TeamsAndRiders(
            teams = teams,
            riders = riders,
        )
    }

}

data class TeamsAndRiders(
    val teams: List<Team>,
    val riders: List<Rider>
)

data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val country: String,
    val bike: String,
    val jerseyUrl: String,
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

data class Rider(
    val id: String,
    val firstName: String,
    val lastName: String,
    val country: String,
    val website: String?,
    val birthDate: LocalDate,
    val birthPlace: String?,
    val weight: Int?,
    val height: Int?,
    val photoUrl: String,
) {
    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}