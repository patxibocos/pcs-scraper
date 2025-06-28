package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Rider
import it.skrape.selects.Doc
import it.skrape.selects.html5.a
import it.skrape.selects.html5.div
import it.skrape.selects.html5.h1
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.slf4j.Logger
import java.net.URI
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class PCSRidersScraper(
    private val docFetcher: DocFetcher,
    private val logger: Logger = KotlinLogging.logger {},
    private val pcsUrl: String = PCS_URL,
) : RidersScraper {

    override suspend fun scrapeRiders(season: Int, requiredRiders: List<Pair<String, String>>): List<Rider> =
        coroutineScope {
            logger.info("Scraping riders for $season season")
            logger.info("Scraping required riders first (${requiredRiders.size})")
            val requiredPcsRiders = requiredRiders.map { (riderId, riderFullName) ->
                async {
                    getRider("rider/$riderId", riderFullName)
                }
            }.awaitAll()
            val teamsRiders = getTeamsUrls(season).map { teamUrl -> getTeamRiders(teamUrl) }
            val pcsRiders = teamsRiders
                .map {
                    logger.info("Scraping riders for team ${it.teamName}")
                    it.riderIdsToNames.map { (riderUrl, riderFullName) ->
                        async {
                            getRider(riderUrl, riderFullName)
                        }
                    }.awaitAll()
                }
                .flatten()
                .plus(requiredPcsRiders)
                .distinctBy { it.url }
            val usCollator = Collator.getInstance(Locale.US)
            val ridersComparator = compareBy(usCollator) { r: Rider -> r.lastName.lowercase() }
                .thenBy(usCollator) { r: Rider -> r.firstName.lowercase() }
            pcsRiders.map(::pcsRiderToRider).sortedWith(ridersComparator)
        }

    private suspend fun getTeamsUrls(season: Int): List<String> {
        val teamsURL = buildURL("teams.php?year=$season&s=worldtour")
        val teamsDoc = docFetcher.getDoc(teamsURL)
        return teamsDoc.findAll(".list.fs14.columns2.mob_columns1 a").map { it.attribute("href") }
    }

    private suspend fun getTeamRiders(teamUrl: String): TeamRiders {
        val teamURL = buildURL(teamUrl)
        val teamDoc = docFetcher.getDoc(teamURL) { relaxed = true }
        val pageTitleMain = teamDoc.findFirst(".page-title > .main")
        val teamName = pageTitleMain.h1 { findFirst { text } }.substringBefore('(').trim()
        val riderIdsToNames =
            teamDoc.findFirst(".ridersTab[data-code=name]").findAll("a").map { it.attribute("href") to it.text }
        return TeamRiders(teamName, riderIdsToNames)
    }

    private suspend fun getRider(riderUrl: String, riderFullName: String): PCSRider {
        val riderURL = buildURL(riderUrl)
        val riderDoc = docFetcher.getDoc(riderURL) { relaxed = true }
        val infoContent = riderDoc.findFirst(".rdr-info-cont")
        val website = riderDoc.getWebsite()
        val birthDate = infoContent.ownText.split(' ').take(3).joinToString(" ")
        val birthPlaceWeightAndHeight = infoContent.findFirst(":last-child").findFirst { text }.split(' ')
        val birthPlaceWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("birth") }
        val birthPlace = if (birthPlaceWordIndex != -1) birthPlaceWeightAndHeight[birthPlaceWordIndex + 1] else null
        val weightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("weight") }
        val weight = if (weightWordIndex != -1) birthPlaceWeightAndHeight[weightWordIndex + 1] else null
        val heightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("height") }
        val height = if (heightWordIndex != -1) birthPlaceWeightAndHeight[heightWordIndex + 1] else null
        val imageUrl = riderDoc.findFirst("img").attribute("src")
        val uciRankingPosition = riderDoc.a { findAll { filter { it.text == "UCI World" } } }
            .firstOrNull()?.parent?.parent?.children?.last()?.text
        val country = riderDoc.div { findAll { filter { it.text == "Nationality:" } } }
            .firstOrNull()?.parent?.findFirst("span.flag")?.classNames?.last() ?: error("Country not found")
        return PCSRider(
            url = riderUrl,
            fullName = riderFullName,
            country = country,
            website = website,
            birthDate = birthDate,
            birthPlace = birthPlace,
            weight = weight,
            height = height,
            photo = imageUrl,
            uciRankingPosition = uciRankingPosition,
        )
    }

    private fun pcsRiderToRider(pcsRider: PCSRider): Rider {
        val (firstName, lastName) = pcsRider.getFirstAndLastName(pcsRider.fullName)
        val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
        val birthDate = try {
            LocalDate.parse(pcsRider.birthDate, dateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
        return Rider(
            id = pcsRider.url.split("/").last(),
            firstName = firstName,
            lastName = lastName,
            country = pcsRider.country.uppercase(),
            website = pcsRider.website,
            birthDate = birthDate,
            birthPlace = pcsRider.birthPlace,
            weight = pcsRider.weight?.toFloat()?.toInt(),
            height = (pcsRider.height?.toFloat()?.times(100))?.toInt(),
            photo = buildURL(pcsRider.photo),
            uciRankingPosition = pcsRider.uciRankingPosition?.toIntOrNull(),
        )
    }

    private fun Doc.getWebsite(): String? =
        findFirst(".sites .website").takeIf {
            it.parents.isNotEmpty()
        }?.parent?.findFirst("a")?.attribute("href")

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()
}

private data class TeamRiders(val teamName: String, val riderIdsToNames: List<Pair<String, String>>)
