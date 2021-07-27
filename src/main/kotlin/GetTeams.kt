import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.util.*

class GetTeams(private val pcsParser: PCSParser) {

    operator fun invoke(season: Int): List<Team> {
        val pcsTeams = pcsParser.getTeamsUrls(season).map(pcsParser::getTeam)
        return pcsTeams.map(pcsParser::pcsTeamToTeam).sortedBy { it.name }
    }

}

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
    val riders: List<String>,
) {
    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}