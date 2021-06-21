import java.nio.file.Paths

const val pcsUrl = "https://www.procyclingstats.com"

fun main() {
    val cache = Cache(Paths.get("."))
    val docFetcher = DocFetcher(cache)
    val pcsParser = PCSParser(docFetcher, pcsUrl)

    val season = 2021
    val (teams, riders) = GetTeamsAndRiders(pcsParser = pcsParser)(season = season)

    teams.forEach(::println)
    riders.forEach(::println)
    println("${teams.size} teams scrapped")
    println("${riders.size} riders scrapped")
}