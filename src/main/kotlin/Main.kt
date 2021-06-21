import java.nio.file.Paths

const val pcsUrl = "https://www.procyclingstats.com"

fun main() {
    val cache = Cache(Paths.get("."))
    val docFetcher = DocFetcher(cache)
    val pcsParser = PCSParser(docFetcher, pcsUrl)
    val serializer = Serializer()

    val season = 2021
    val teamsAndRiders = GetTeamsAndRiders(pcsParser = pcsParser)(season = season)

    println(serializer.serialize(teamsAndRiders))
}