import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

class GetWorldTourRaces(private val pcsParser: PCSParser) {

    operator fun invoke(): List<Race> {
        val pcsRaces = pcsParser.getRacesUrls().map(pcsParser::getRace).sortedBy { it.startDate }
        return pcsRaces.map(pcsParser::pcsRaceToRace)
    }

}

@Serializable
data class Race(
    val id: String,
    val name: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val website: String?,
    val stages: List<Stage>,
)

@Serializable
data class Stage(
    val id: String,
    @Contextual val startDate: LocalDate,
)
