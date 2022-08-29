package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Team
import it.skrape.selects.Doc
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
        }.awaitAll().map(::pcsTeamToTeam).sortedBy { it.name }
    }

    private suspend fun getTeamsUrls(season: Int): List<String> {
        val teamsURL = buildURL("teams.php?year=$season&s=worldtour")
        val teamsDoc = docFetcher.getDoc(teamsURL)
        return teamsDoc.findAll(".list.fs14.columns2.mob_columns1 a").map { it.attribute("href") }
    }

    private suspend fun getTeam(teamUrl: String, season: Int): PCSTeam {
        val teamURL = buildURL(teamUrl)
        val teamDoc = docFetcher.getDoc(teamURL) { relaxed = true }
        val infoList = teamDoc.ul { withClass = "infolist"; this }
        val status = infoList.findFirst("li").findSecond("div").ownText
        val abbreviation = infoList.findSecond("li").findSecond("div").ownText
        val bike = infoList.findThird("li").findFirst("a").ownText
        val website = teamDoc.getWebsite()

        fun getJerseyImageFromUci(): String {
            val uciCategory = when (status) {
                "WT" -> "WTT"
                "PRT" -> "PRT"
                else -> ""
            }
            return "https://ucibws.uci.ch/api/WebResources/ModulesData/Teams/$season/ROA/Jerseys/$uciCategory/ROA-${uciCategory}_${abbreviation}_$season.jpg"
        }

        val jersey = getJerseyImageFromUci()
        val pageTitleMain = teamDoc.findFirst(".page-title > .main")
        val teamName = pageTitleMain.h1 { findFirst { text } }.substringBefore('(').trim()
        val country = teamDoc.getCountry()
        val year = pageTitleMain.findLast("span").ownText.toInt()
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
        teamDoc.findAll(".ttabs.tabb a").map { it.attribute("href") }

    private fun pcsTeamToTeam(pcsTeam: PCSTeam): Team =
        Team(
            id = pcsTeam.url.split("/").last(),
            name = pcsTeam.name,
            status = Team.Status.valueOf(pcsTeam.status),
            abbreviation = pcsTeam.abbreviation,
            country = pcsTeam.country.uppercase(),
            bike = pcsTeam.bike,
            jersey = buildURL(pcsTeam.jersey),
            website = pcsTeam.website,
            year = pcsTeam.year,
            riders = pcsTeam.riders.map { it.split("/").last() },
        )

    private fun Doc.getWebsite(): String? =
        findFirst(".sites .website").takeIf {
            it.parents.isNotEmpty()
        }?.parent?.findFirst("a")?.attribute("href")

    private fun Doc.getCountry(): String =
        findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()
}
