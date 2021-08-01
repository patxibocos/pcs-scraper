import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

class GetWorldTourCalendar(private val pcsParser: PCSParser) {

    operator fun invoke(): List<Race> {
        val pcsRaces = pcsParser.getWorldTourCalendarRacesUrls().map(pcsParser::getRace).sortedBy { it.startDate }
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
)
