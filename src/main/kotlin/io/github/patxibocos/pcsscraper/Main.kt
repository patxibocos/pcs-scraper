package io.github.patxibocos.pcsscraper

import io.github.patxibocos.pcsscraper.document.Cache
import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.export.Exporter
import io.github.patxibocos.pcsscraper.export.Format
import io.github.patxibocos.pcsscraper.scraper.PCSScraper
import io.github.patxibocos.pcsscraper.scraper.RacesScraper
import io.github.patxibocos.pcsscraper.scraper.RidersScraper
import io.github.patxibocos.pcsscraper.scraper.TeamsScraper
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

const val pcsUrl = "https://www.procyclingstats.com"

fun main(args: Array<String>) {
    val (season, cachePath, destination, format, skipCache) = getAppArgs(args)

    val cache = cachePath?.let { Cache(Paths.get(it)) }
    val docFetcher = DocFetcher(cache, skipCache)
    val pcsScraper = PCSScraper(docFetcher, pcsUrl)
    val teamsScraper: TeamsScraper = pcsScraper
    val ridersScraper: RidersScraper = pcsScraper
    val racesScraper: RacesScraper = pcsScraper

    runBlocking {
        val teams = teamsScraper.scrapeTeams(season = season)
        val riders = ridersScraper.scrapeRiders(season = season)
        val races = racesScraper.scrapeRaces()

        val exporter: Exporter = Exporter.from(destination, format)
        exporter.exportTeams(teams)
        exporter.exportRiders(riders)
        exporter.exportRacesWithStages(races)
    }
}

private data class AppArgs(
    val season: Int,
    val cachePath: String?,
    val destination: String,
    val format: Format,
    val skipCache: Boolean,
)

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("pcs-scraper")
    val season by parser.option(ArgType.Int, shortName = "s", description = "Season").required()
    val cachePath by parser.option(ArgType.String, shortName = "c", description = "Cache path")
    val destination by parser.option(ArgType.String, shortName = "d", description = "Destination path").required()
    val format by parser.option(
        ArgType.Choice(Format.values().map { it.name.lowercase() }, { it }),
        shortName = "f",
        description = "Output file format"
    ).required()
    val skipCache by parser.option(ArgType.Boolean, shortName = "sc", description = "Skip cache").default(false)
    parser.parse(args)
    return AppArgs(
        season = season,
        cachePath = cachePath,
        destination = destination,
        format = Format.valueOf(format.uppercase()),
        skipCache = skipCache,
    )
}
