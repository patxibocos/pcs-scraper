package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team

interface TeamsScraper {
    fun scrapeTeams(season: Int): List<Team>
}

interface RidersScraper {
    fun scrapeRiders(season: Int): List<Rider>
}

interface RacesScraper {
    fun scrapeRaces(): List<Race>
}
