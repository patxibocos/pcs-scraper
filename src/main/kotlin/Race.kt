import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Race(
    val id: String,
    val name: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val website: String?,
    val stages: List<Stage>,
) {
    @Serializable
    data class Stage(
        val id: String,
        @Contextual val startDate: LocalDate,
    )
}
