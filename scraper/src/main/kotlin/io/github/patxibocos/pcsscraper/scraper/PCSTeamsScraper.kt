package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Team
import it.skrape.selects.Doc
import it.skrape.selects.html5.a
import it.skrape.selects.html5.h1
import it.skrape.selects.html5.ul
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.slf4j.Logger
import java.net.URI
import java.net.URL

class PCSTeamsScraper(
    private val docFetcher: DocFetcher,
    private val logger: Logger = KotlinLogging.logger {},
    private val pcsUrl: String = PCS_URL,
) :
    TeamsScraper {

    override suspend fun scrapeTeams(season: Int): List<Team> = coroutineScope {
        logger.info("Scraping teams for $season season")
        getTeamsUrls(season).map { teamUrl ->
            async { getTeam(teamUrl, season) }
        }.awaitAll().mapNotNull(::pcsTeamToTeam).sortedBy { it.name }
    }

    private suspend fun getTeamsUrls(season: Int): List<String> {
        val teamsURL = buildURL("teams.php?year=$season&s=worldtour")
        val teamsDoc = docFetcher.getDoc(teamsURL)
        return teamsDoc.findAll(".list.fs14.columns2.mob_columns1 a").map { it.attribute("href") }
    }

    private suspend fun getTeam(teamUrl: String, season: Int): PCSTeam {
        val teamURL = buildURL(teamUrl)
        val teamDoc = docFetcher.getDoc(teamURL) { relaxed = true }
        val status: String
        val abbreviation: String
        val bike: String
        // Workaround because currently this section is missing for this team
        if (teamUrl == "team/lotto-intermarche-2026") {
            status = "WT"
            abbreviation = "LOI"
            bike = ""
        } else {
            val infoList = teamDoc.ul { withClass = "infolist"; this }
            status = infoList.findFirst("li").findSecond("div").ownText
            abbreviation = infoList.findSecond("li").findSecond("div").ownText.uppercase()
            bike = infoList.findFirst("a[href^='brand']").ownText
        }
        val website = teamDoc.findAll { filter { it.findFirst("a").text == "SITE" } }.firstOrNull()
            ?.a { findFirst { attribute("href") } }

        fun getJerseyImageFromUci(): String {
            val uciCategory = when (status) {
                "WT" -> "WTT"
                "PRT" -> "PRT"
                else -> ""
            }
            return "https://api.uci.ch/v1/ucibws/WebResources/ModulesData/Teams/$season/ROA/Jerseys/$uciCategory/ROA-${uciCategory}_${abbreviation}_$season.jpg"
        }

        val jersey = getJerseyImageFromUci()
        val pageTitle = teamDoc.findFirst(".page-title")
        val teamName = pageTitle.h1 { findFirst { text } }.substringBefore('(').trim()
        val country = pageTitle.findFirst(".title > span:nth-child(2)").classNames.last()
        val year = pageTitle.findFirst(".subtitle > h2").ownText.toInt()
        return PCSTeam(
            url = teamUrl,
            name = teamName,
            status = status,
            abbreviation = abbreviation,
            country = country,
            bike = bike,
            website = website,
            jersey = jersey,
            year = year,
            riders = getTeamRiders(teamDoc),
        )
    }

    private fun getTeamRiders(teamDoc: Doc): List<String> =
        teamDoc.findFirst(".teamlist").findAll("a").map { it.attribute("href") }

    private fun pcsTeamToTeam(pcsTeam: PCSTeam): Team? {
        val teamStatus = try {
            Team.Status.valueOf(pcsTeam.status)
        } catch (_: Exception) {
            return null
        }
        return Team(
            id = pcsTeam.url.split("/").last(),
            name = pcsTeam.name,
            status = teamStatus,
            abbreviation = pcsTeam.abbreviation,
            country = pcsTeam.country.uppercase(),
            bike = pcsTeam.bike,
            jersey = buildURL(pcsTeam.jersey),
            website = pcsTeam.website,
            year = pcsTeam.year,
            riders = pcsTeam.riders.map { it.split("/").last() },
        )
    }

    private fun buildURL(path: String): URL {
        val uri = URI(pcsUrl).resolve("/").resolve(path)
        return URL(uri.toASCIIString())
    }
}
