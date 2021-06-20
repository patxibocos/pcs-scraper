import java.nio.file.Paths

fun main() {
    val cache = Cache(Paths.get("."))
    val docFetcher = DocFetcher(cache)
    val scrapper = Scrapper()

    val season = 2021
    val teamsDoc = docFetcher.getDoc("teams.php?year=$season&filter=Filter&s=worldtour")
    val teams = scrapper.parseTeamsDoc(teamsDoc).map { teamUrl ->
        docFetcher.getDoc(teamUrl) { relaxed = true }
    }.map(scrapper::parseTeamDoc)
    val riders = teams.flatMap(Scrapper.Team::riders).map { teamRider ->
        val riderDoc = docFetcher.getDoc(teamRider.id) { relaxed = true }
        scrapper.parseRiderDoc(riderDoc, teamRider.fullName)
    }
    println("${teams.size} teams scrapped")
    println("${riders.size} riders scrapped")
}