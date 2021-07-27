import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

class GetUpcomingRaces(private val pcsParser: PCSParser) {

    operator fun invoke(): List<Race> {
        return emptyList()
    }

}

@Serializable
data class Race(
    val name: String,
    val category: Category,
    val uciTour: UciTour,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val website: String?,
    val departure: String?,
    val arrival: String?,
    val riders: List<String>,
)

enum class Category {}
enum class UciTour {}
