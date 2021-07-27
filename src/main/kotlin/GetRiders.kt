import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.util.*

class GetRiders(private val pcsParser: PCSParser) {

    operator fun invoke(season: Int): List<Rider> {
        val pcsTeams = pcsParser.getTeamsUrls(season).map(pcsParser::getTeam)
        val pcsRiders = pcsTeams
            .flatMap(PCSTeam::riders)
            .map { (riderUrl, riderFullName) -> pcsParser.getRider(riderUrl, riderFullName) }
            .distinctBy { it.url }
        val usCollator = Collator.getInstance(Locale.US)
        pcsTeams.map(pcsParser::pcsTeamToTeam).sortedBy { it.name }
        val ridersComparator = compareBy(usCollator) { r: Rider -> r.lastName.lowercase() }
            .thenBy(usCollator) { r: Rider -> r.firstName.lowercase() }
        return pcsRiders.map(pcsParser::pcsRiderToRider).sortedWith(ridersComparator)
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