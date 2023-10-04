package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Team
import it.skrape.selects.Doc
import it.skrape.selects.html5.h1
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
        val pageTitleMain = teamDoc.findFirst(".page-title > .main")
        val teamNameAndStatus = pageTitleMain.h1 { findFirst { text } }
        val status =
            teamNameAndStatus.substring(teamNameAndStatus.lastIndexOf('(') + 1, teamNameAndStatus.lastIndexOf(')'))
        val teamId = teamUrl.substring(teamUrl.lastIndexOf('/') + 1)
        val abbreviation = teamToAbbreviation[teamId]
            ?: throw IllegalArgumentException("Failed to resolve team abbreviation for team url: $teamId")
        val bike = ""
        val website = teamToWebsite[teamId]

        fun getJerseyImageFromUci(): String {
            val uciCategory = when (status) {
                "WT" -> "WTT"
                "PRT" -> "PRT"
                else -> ""
            }
            return "https://api.uci.ch/v1/ucibws/WebResources/ModulesData/Teams/$season/ROA/Jerseys/$uciCategory/ROA-${uciCategory}_${abbreviation}_$season.jpg"
        }

        val jersey = getJerseyImageFromUci()
        val teamName = teamNameAndStatus.substringBefore('(').trim()
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
        teamDoc.findFirst(".ridersTab[data-code=name]").findAll("a").map { it.attribute("href") }

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

    private fun Doc.getCountry(): String =
        findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()

    private val teamToAbbreviation = mapOf(
        "ag2r-citroen-team-2023" to "ACT",
        "alpecin-deceuninck-2023" to "ADC",
        "astana-qazaqstan-team-2023" to "AST",
        "bora-hansgrohe-2023" to "BOH",
        "bahrain-victorious-2023" to "TBV",
        "bingoal-wb-2023" to "BWB",
        "bolton-equities-black-spoke-2023" to "BEB",
        "burgos-bh-2023" to "BBH",
        "caja-rural-seguros-rga-2023" to "CJR",
        "cofidis-2023" to "COF",
        "ef-education-easypost-2023" to "EFE",
        "eolo-kometa-2023" to "EOK",
        "equipo-kern-pharma-2023" to "EKP",
        "euskaltel-euskadi-2023" to "EUS",
        "green-project-bardiani-csf-faizane-2023" to "GBF",
        "groupama-fdj-2023" to "GFC",
        "human-powered-health-2023" to "HPM",
        "ineos-grenadiers-2023" to "IGD",
        "intermarche-circus-wanty-2023" to "ICW",
        "israel-premier-tech-2023" to "IPT",
        "team-jumbo-visma-2023" to "TJV",
        "lidl-trek-2023" to "LTK",
        "lotto-dstny-2023" to "LTD",
        "movistar-team-2023" to "MOV",
        "q365-pro-cycing-team" to "Q36",
        "soudal-quick-step-2023" to "SOQ",
        "team-arkea-samsic-2023" to "ARK",
        "team-corratec-selle-italia-2023" to "COR",
        "team-flanders--baloise-2023" to "TFB",
        "team-jayco-alula-2023" to "JAY",
        "team-novo-nordisk-2023" to "TNN",
        "team-dsm-firmenich-2023" to "DSM",
        "team-totalenergies-2023" to "TEN",
        "tudor-pro-cycling-team-2023" to "TUD",
        "uae-team-emirates-2023" to "UAD",
        "uno-x-pro-cycling-team-2023" to "UXT",
    )

    private val teamToWebsite = mapOf(
        "ag2r-citroen-team-2023" to "https://www.ag2rcitroenteam.com/en/",
        "alpecin-deceuninck-2023" to "https://www.alpecin-deceuninck.com/",
        "astana-qazaqstan-team-2023" to "https://www.astana-qazaqstan.com/",
        "bora-hansgrohe-2023" to "https://www.bora-hansgrohe.com",
        "bahrain-victorious-2023" to "https://bahraincyclingteam.com/",
        "bingoal-wb-2023" to "https://www.wbca.be/",
        "bolton-equities-black-spoke-2023" to "https://blackspoke.co.nz/",
        "burgos-bh-2023" to "https://www.burgosproteam.com/",
        "caja-rural-seguros-rga-2023" to "https://www.teamcajarural-segurosrga.com/",
        "cofidis-2023" to "https://www.equipecofidis.com/fr/accueil",
        "ef-education-easypost-2023" to "https://www.efprocycling.com",
        "eolo-kometa-2023" to "https://eolokometacyclingteam.com/",
        "equipo-kern-pharma-2023" to "https://equipokernpharma.com/",
        "euskaltel-euskadi-2023" to "https://www.fundacioneuskadi.eus/",
        "green-project-bardiani-csf-2023" to "https://www.greenprojectbardianicsffaizane.com/",
        "groupama-fdj-2023" to "https://www.equipecycliste-groupama-fdj.fr",
        "human-powered-health-2023" to "https://humanpoweredhealthcycling.com/",
        "ineos-grenadiers-2023" to "https://www.ineosgrenadiers.com/",
        "intermarche-circus-wanty-2023" to "https://intermarche-circus-wanty.eu",
        "israel-premier-tech-2023" to "https://www.israelpremiertech.com/",
        "team-jumbo-visma-2023" to "https://www.teamjumbovisma.nl",
        "lidl-trek-2023" to "https://racing.trekbikes.com/",
        "lotto-dstny-2023" to "https://www.lottodstny.be/",
        "movistar-team-2023" to "https://www.movistarteam.com/",
        "q365-pro-cycing-team" to "https://www.q36-5procycling.com/",
        "soudal-quick-step-2023" to "https://www.soudal-quickstepteam.com/",
        "team-arkea-samsic-2023" to "https://www.team-arkea-samsic.fr",
        "team-corratec-selle-italia-2023" to "https://www.toscanafactoryteam.com/",
        "team-flanders--baloise-2023" to "https://www.teamflanders-baloise.be/",
        "team-jayco-alula-2023" to "https://www.greenedgecycling.com/",
        "team-novo-nordisk-2023" to "https://www.teamnovonordisk.com/",
        "team-dsm-firmenich-2023" to "https://www.team-dsm-firmenich.com",
        "team-totalenergies-2023" to "https://teamtotalenergies.com",
        "tudor-pro-cycling-team-2023" to "https://www.tudorprocycling.com/",
        "uae-team-emirates-2023" to "http://www.uaeteamemirates.com/",
        "uno-x-pro-cycling-team-2023" to "https://unoxteam.no/",
    )
}
