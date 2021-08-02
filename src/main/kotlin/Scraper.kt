interface TeamsScraper {
    fun scrapeTeams(season: Int): List<Team>
}

interface RidersScraper {
    fun scrapeRiders(season: Int): List<Rider>
}

interface RacesScraper {
    fun scrapeRaces(): List<Race>
}
