package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team

interface TeamsScraper {
    suspend fun scrapeTeams(season: Int): List<Team>
}

interface RidersScraper {
    suspend fun scrapeRiders(season: Int, requiredRiders: List<Pair<String, String>>): List<Rider>
}

interface RacesScraper {
    suspend fun scrapeRaces(season: Int, teamIdMapper: (String) -> String?): List<Race>
}
