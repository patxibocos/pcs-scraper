package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team

interface TeamsScraper {
    suspend fun scrapeTeams(season: Int): List<Team>
}

interface RidersScraper {
    suspend fun scrapeRiders(season: Int): List<Rider>
}

interface RacesScraper {
    suspend fun scrapeRaces(): List<Race>
}
