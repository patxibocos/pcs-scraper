package io.github.patxibocos.pcsscraper

import io.github.patxibocos.pcsscraper.document.Cache
import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.export.Exporter
import io.github.patxibocos.pcsscraper.export.Format
import io.github.patxibocos.pcsscraper.scraper.PCSRacesScraper
import io.github.patxibocos.pcsscraper.scraper.PCSRidersScraper
import io.github.patxibocos.pcsscraper.scraper.PCSTeamsScraper
import io.github.patxibocos.pcsscraper.scraper.RacesScraper
import io.github.patxibocos.pcsscraper.scraper.RidersScraper
import io.github.patxibocos.pcsscraper.scraper.TeamsScraper
import io.github.patxibocos.pcsscraper.scraper.teamIdMapper
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.multiple
import kotlinx.cli.required
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.slf4j.Logger
import java.nio.file.Paths
import kotlin.time.Duration

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (season, cachePath, destination, formats, skipCache, scrapTimeout, retryDelay) = appArgs

    val scrapTimeoutDuration = Duration.parse(scrapTimeout)
    val retryDelayDuration = Duration.parse(retryDelay)

    val cache = cachePath?.let { Cache(Paths.get(it)) }
    val docFetcher = DocFetcher(cache, skipCache, retryDelayDuration)
    val teamsScraper: TeamsScraper = PCSTeamsScraper(docFetcher)
    val ridersScraper: RidersScraper = PCSRidersScraper(docFetcher)
    val racesScraper: RacesScraper = PCSRacesScraper(docFetcher)

    runBlocking {
        val logger: Logger = KotlinLogging.logger {}

        logger.info("Running scraper with arguments: $appArgs")
        val data = async {
            val teams = teamsScraper.scrapeTeams(season = season)
            val races =
                racesScraper.scrapeRaces(
                    season = season,
                    teamIdMapper = teamIdMapper(teams),
                )
            val participantRiders = races.map { it.stages }.flatten().map { stage ->
                buildList<Pair<String, String>> {
                    // Time only if stage is not TTT
                    if (stage.stageType != Race.Stage.StageType.TEAM_TIME_TRIAL) {
                        addAll(stage.stageResults.time.map { it.participant to it.name })
                    }
                    addAll(stage.stageResults.youth.map { it.participant to it.name })
                    addAll(stage.stageResults.kom.map { it.points.map { it.participant to it.name } }.flatten())
                    addAll(stage.stageResults.points.map { it.points.map { it.participant to it.name } }.flatten())

                    addAll(stage.generalResults.time.map { it.participant to it.name })
                    addAll(stage.generalResults.youth.map { it.participant to it.name })
                    addAll(stage.generalResults.kom.map { it.participant to it.name })
                    addAll(stage.generalResults.points.map { it.participant to it.name })
                }
            }.flatten().distinct()
            val riders = ridersScraper.scrapeRiders(season = season, requiredRiders = emptyList())
            Triple(teams, riders, races)
        }

        val (teams, riders, races) = withTimeout(scrapTimeoutDuration) {
            data.await()
        }

        formats.map { format ->
            val exporter: Exporter = Exporter.from(destination, format)
            logger.info("Exporting to $format")
            async { exporter.export(teams, riders, races) }
        }.awaitAll()
    }
}

private data class AppArgs(
    val season: Int,
    val cachePath: String?,
    val destination: String,
    val formats: List<Format>,
    val skipCache: Boolean,
    val scrapTimeout: String,
    val retryDelay: String,
)

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("pcs-scraper")
    val season by parser.option(ArgType.Int, shortName = "s", description = "Season").required()
    val cachePath by parser.option(ArgType.String, shortName = "c", description = "Cache path")
    val destination by parser.option(ArgType.String, shortName = "d", description = "Destination path").default("out")
    val formats by parser.option(
        ArgType.Choice(Format.entries.map { it.name.lowercase() }, { it }),
        shortName = "f",
        description = "Output file format",
    ).required().multiple()
    val skipCache by parser.option(ArgType.Boolean, shortName = "sc", description = "Skip cache").default(false)
    val scrapTimeout by parser.option(ArgType.String, shortName = "st", description = "Scrap timeout").default("20m")
    val retryDelay by parser.option(ArgType.String, shortName = "rd", description = "Retry delay").default("1s")
    parser.parse(args)
    return AppArgs(
        season = season,
        cachePath = cachePath,
        destination = destination,
        formats = formats.map { Format.valueOf(it.uppercase()) },
        skipCache = skipCache,
        scrapTimeout = scrapTimeout,
        retryDelay = retryDelay,
    )
}
