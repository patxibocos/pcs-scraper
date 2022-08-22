package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Race
import it.skrape.selects.Doc
import it.skrape.selects.DocElement
import it.skrape.selects.html5.a
import it.skrape.selects.html5.span
import it.skrape.selects.html5.td
import it.skrape.selects.html5.thead
import it.skrape.selects.html5.tr
import it.skrape.selects.html5.ul
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.slf4j.Logger
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PCSRacesScraper(
    private val docFetcher: DocFetcher,
    private val logger: Logger = KotlinLogging.logger {},
    private val pcsUrl: String = PCS_URL,
) :
    RacesScraper {

    override suspend fun scrapeRaces(season: Int): List<Race> = coroutineScope {
        logger.info("Scraping races for $season season")
        val pcsRaces = getRacesUrls(season).map { raceUrl -> async { getRace(raceUrl) } }.awaitAll()
        pcsRaces.map(::pcsRaceToRace).sortedBy { it.startDate }
    }

    private suspend fun getRacesUrls(season: Int): List<String> {
        val calendarUrl = buildURL("races.php?year=$season&circuit=1")
        val calendarDoc = docFetcher.getDoc(calendarUrl)
        return calendarDoc.findAll("table tr:not(.striked) > td:nth-child(3) > a").map { it.attribute("href") }
            .map { it.removeSuffix("/preview").removeSuffix("/startlist") + "/overview" }
    }

    private suspend fun getRace(raceUrl: String): PCSRace = coroutineScope {
        val raceURL = buildURL(raceUrl)
        val raceDoc = docFetcher.getDoc(raceURL) { relaxed = true }
        val infoList = raceDoc.ul { withClass = "infolist"; this }
        val header = raceDoc.findAll(".page-topnav > ul > li")
        val participantsIndex = header.indexOfFirst { it.text == "Startlist" }
        val resultsIndex = header.indexOfFirst { it.text == "Results" }
        val stagesIndex = header.indexOfFirst { it.text.startsWith("Stages") }
        val raceParticipantsUrl = header[participantsIndex].a { findFirst { attribute("href") } }
        val raceResultUrl = header[resultsIndex].a { findFirst { attribute("href") } }
        val stagesUrl = header[stagesIndex].a { findFirst { attribute("href") } }
        val startDate = infoList.findFirst("li").findSecond("div").ownText
        val endDate = infoList.findSecond("li").findSecond("div").ownText
        val name = raceDoc.findFirst(".main > h1").text
        val stages = if (startDate == endDate) {
            listOf(getStage(raceResultUrl, true))
        } else {
            logger.info("Scraping stages for race $name")
            getStages(stagesUrl)
        }
        val country = raceDoc.getCountry()
        val websites = raceDoc.findAll("ul.list.circle.bluelink.fs14 a").map { it.attribute("href") }
        val website = websites.firstOrNull {
            !it.contains("twitter") && !it.contains("facebook") && !it.contains("instagram") && it.trim()
                .isNotEmpty()
        }
        val startList = getRaceStartList(raceParticipantsUrl)
        val result = stages.findLast {
            it.gcResult.isNotEmpty()
        }?.gcResult ?: emptyList()
        PCSRace(
            url = raceUrl,
            name = name,
            country = country,
            startDate = startDate,
            endDate = endDate,
            website = website,
            stages = stages,
            startList = startList,
            result = result,
        )
    }

    private suspend fun getRaceStartList(raceStartListUrl: String): List<PCSTeamParticipation> {
        val raceParticipantsUrl = buildURL(raceStartListUrl)
        val raceStartListDoc = docFetcher.getDoc(raceParticipantsUrl) { relaxed = true }
        val startList = raceStartListDoc.findAll("ul.startlist_v3 > li.team").map {
            val team = it.findFirst("a").attribute("href")
            val riders = it.findAll("ul > li").map { riderDocElement ->
                PCSRiderParticipation(
                    rider = riderDocElement.a { findFirst { attribute("href") } },
                    number = riderDocElement.ownText
                )
            }
            PCSTeamParticipation(
                team = team,
                riders = riders,
            )
        }
        return startList
    }

    private suspend fun getStages(stagesUrl: String): List<PCSStage> = coroutineScope {
        val stagesURI = buildURL(stagesUrl)
        val stagesDoc = docFetcher.getDoc(stagesURI) { relaxed = true }
        val stagesUrls = stagesDoc.findFirst("table.basic > tbody").findAll("tr").map {
            it.findThird("td").findFirst("a").attribute("href")
        }
        stagesUrls.map { stageUrl -> async { getStage(stageUrl, false) } }.awaitAll()
    }

    private suspend fun getStage(stageUrl: String, isSingleDayRace: Boolean): PCSStage = coroutineScope {
        val stageURL = buildURL(stageUrl)
        val stageDoc = docFetcher.getDoc(stageURL) { relaxed = true }
        val infoList = stageDoc.findFirst("ul.infolist")
        val startDate = infoList.findFirst("li > div:nth-child(2)").ownText
        val startTime = infoList.findSecond("li > div:nth-child(2)").ownText
        val startTimeCET = if (startTime.isNotBlank() && startTime != "-") {
            val cetTimePart = startTime.substring(startTime.indexOf('(') + 1)
            cetTimePart.substring(0, 5)
        } else {
            null
        }
        val distance = infoList.findByIndex(4, "li > div:nth-child(2)").ownText
        val type = infoList.findByIndex(7, "li").findFirst("span").classNames.last()
        val stageTitle = stageDoc.findFirst(".sub > span:nth-child(3)").text
        val isIndividualTimeTrial = stageTitle.contains("ITT")
        val isTeamTimeTrial = stageTitle.contains("TTT")
        val departure = infoList.findByIndex(9, "li").findFirst("a").text.ifEmpty { null }
        val arrival = infoList.findByIndex(10, "li").findFirst("a").text.ifEmpty { null }
        val result: List<PCSParticipantResult>
        val gcResult: List<PCSParticipantResult>
        when (isSingleDayRace) {
            // Single day races will never have a gcResult, so we manually set it
            true -> {
                result = getResult(stageDoc.findFirst(".result-cont"))
                gcResult = result
            }

            false -> {
                gcResult = getResult(stageDoc.findSecond(".result-cont"))
                // We skip stage result if GC result is not available, just for consistency
                result = if (gcResult.isEmpty()) {
                    gcResult
                } else {
                    if (isTeamTimeTrial) {
                        getTTTResult(stageDoc.findFirst(".results-ttt"))
                    } else {
                        getResult(stageDoc.findFirst(".result-cont"))
                    }
                }
            }
        }
        PCSStage(
            url = stageUrl,
            startDate = startDate,
            startTimeCET = startTimeCET,
            distance = distance,
            type = type,
            individualTimeTrial = isIndividualTimeTrial,
            teamTimeTrial = isTeamTimeTrial,
            departure = departure,
            arrival = arrival,
            result = result,
            gcResult = gcResult,
        )
    }

    private fun getResult(resultsTable: DocElement): List<PCSParticipantResult> {
        val resultColumns = resultsTable.thead { tr { findAll("th") } }
        val positionColumnIndex = resultColumns.indexOfFirst { it.ownText == "Rnk" }
        val riderColumnIndex = resultColumns.indexOfFirst { it.ownText == "Rider" }
        val timeColumnIndex = resultColumns.indexOfFirst { it.ownText == "Time" }
        val result = resultsTable.findAll("tbody > tr").map {
            val position = it.td { findByIndex(positionColumnIndex) }.ownText
            val rider = it.td { findByIndex(riderColumnIndex) }.a { findFirst { attribute("href") } }
            val time = it.td { findByIndex(timeColumnIndex) }.ownText.ifEmpty {
                it.td { findByIndex(timeColumnIndex) }.span { findFirst { ownText } }
            }
            PCSParticipantResult(
                position = position,
                participant = rider,
                time = time,
            )
        }
        return result.takeIf {
            it.size >= 3
        } ?: emptyList()
    }

    private fun getTTTResult(resultsTable: DocElement): List<PCSParticipantResult> {
        val resultColumns = resultsTable.thead { tr { findAll("th") } }
        val positionColumnIndex = resultColumns.indexOfFirst { it.ownText == "Pos." }
        val teamColumnIndex = resultColumns.indexOfFirst { it.ownText == "Team" }
        val timeColumnIndex = resultColumns.indexOfFirst { it.ownText == "Time" }
        val result = resultsTable.findAll("tbody > tr.team").map {
            val position = it.td { findByIndex(positionColumnIndex) }.ownText
            val team = it.td { findByIndex(teamColumnIndex) }.a { findFirst { attribute("href") } }
            val time = it.td { findByIndex(timeColumnIndex) }.ownText
            PCSParticipantResult(
                position = position,
                participant = team,
                time = time,
            )
        }
        return result.takeIf {
            it.size >= 3
        } ?: emptyList()
    }

    private fun pcsRaceToRace(pcsRace: PCSRace): Race {
        val raceId = pcsRace.url.split("/").dropLast(1).takeLast(2).joinToString("-")
        return Race(
            id = raceId,
            name = pcsRace.name,
            country = pcsRace.country.uppercase(),
            startDate = LocalDate.parse(pcsRace.startDate, DateTimeFormatter.ISO_LOCAL_DATE),
            endDate = LocalDate.parse(pcsRace.endDate, DateTimeFormatter.ISO_LOCAL_DATE),
            website = pcsRace.website,
            stages = pcsRace.stages.map { pcsStageToStage(it) },
            startList = pcsRace.startList.map { pcsTeamParticipationToTeamParticipation(it) },
            result = pcsParticipantResultToRiderResult(pcsRace.result)
        )
    }

    private fun pcsParticipantResultToTeamResult(pcsParticipantResults: List<PCSParticipantResult>): List<Race.ParticipantResult> {
        if (pcsParticipantResults.isEmpty()) {
            return emptyList()
        }
        return pcsParticipantResults.take(10).map {
            val position = it.position.toInt()
            val team = it.participant.split("/").last()
            val (minutes, seconds) = it.time.split(":").map(String::toInt)
            val time = (minutes.minutes + seconds.seconds).inWholeSeconds
            Race.ParticipantResult(position, team, time)
        }
    }

    private fun pcsParticipantResultToRiderResult(pcsParticipantResults: List<PCSParticipantResult>): List<Race.ParticipantResult> {
        if (pcsParticipantResults.isEmpty()) {
            return emptyList()
        }
        var firstRiderTime = 0L
        var previousDiff = 0L
        return pcsParticipantResults.take(10).mapNotNull {
            val rider = it.participant.split("/").last()
            if (it.position.toIntOrNull() == null) { // Riders that didn't finish have a position which is not a number
                return@mapNotNull null
            }
            if (it.time != ",,") { // Time being ,, means that it has the same time as the previous rider
                val splits = it.time.split(":")
                if (splits.any { s -> s.toIntOrNull() == null }) {
                    return@mapNotNull null
                }
                val parts = splits.map(String::toInt)
                val timeInSeconds = when (parts.size) {
                    3 -> {
                        val (hours, minutes, seconds) = parts
                        (hours.hours + minutes.minutes + seconds.seconds).inWholeSeconds
                    }

                    2 -> {
                        val (minutes, seconds) = parts
                        (minutes.minutes + seconds.seconds).inWholeSeconds
                    }

                    else -> throw RuntimeException("Unexpected time value: ${it.time}")
                }
                if (firstRiderTime == 0L) {
                    firstRiderTime = timeInSeconds
                } else {
                    previousDiff = timeInSeconds
                }
            }
            Race.ParticipantResult(it.position.toInt(), rider, firstRiderTime + previousDiff)
        }
    }

    private fun pcsStageToStage(pcsStage: PCSStage): Race.Stage {
        // Some dates include time, so for now we just ignore the time part
        val startDateString = pcsStage.startDate.replace(",", "").split(" ").take(3).joinToString(" ")
        val localDate = LocalDate.parse(startDateString, DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        val startDateTime = if (pcsStage.startTimeCET != null) {
            val (hoursCET, minutesCET) = pcsStage.startTimeCET.split(":")
            val cetTimeZoned =
                ZonedDateTime.of(localDate, LocalTime.of(hoursCET.toInt(), minutesCET.toInt()), ZoneId.of("CET"))
            val epochSeconds = cetTimeZoned.toEpochSecond()
            Instant.ofEpochSecond(epochSeconds)
        } else {
            Instant.ofEpochSecond(localDate.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC))
        }
        // p1, p2, p3, p4 and p5 are the only valid values
        val pcsTypeIndex = (1..5).map { "p$it" }.indexOf(pcsStage.type).takeIf { it != -1 }
        val stageId = pcsStage.url.split("/")
            .takeLast(3)
            .joinToString("/")
            .replace("/", "-")
            .replace("result", "stage-1") // For single day races where stage info is on the result page
        val result = if (pcsStage.teamTimeTrial) {
            pcsParticipantResultToTeamResult(pcsStage.result)
        } else {
            pcsParticipantResultToRiderResult(pcsStage.result)
        }
        val gcResult = pcsParticipantResultToRiderResult(pcsStage.gcResult)
        return Race.Stage(
            id = stageId,
            startDateTime = startDateTime,
            distance = pcsStage.distance.split(" ").first().toFloat(),
            profileType = pcsTypeIndex?.let { Race.Stage.ProfileType.values()[pcsTypeIndex] },
            departure = pcsStage.departure,
            arrival = pcsStage.arrival,
            stageType = when {
                pcsStage.individualTimeTrial -> Race.Stage.StageType.INDIVIDUAL_TIME_TRIAL
                pcsStage.teamTimeTrial -> Race.Stage.StageType.TEAM_TIME_TRIAL
                else -> Race.Stage.StageType.REGULAR
            },
            result = result,
            gcResult = gcResult,
        )
    }

    private fun pcsTeamParticipationToTeamParticipation(
        pcsTeamParticipation: PCSTeamParticipation
    ): Race.TeamParticipation {
        val teamId = pcsTeamParticipation.team.split("/").last()
        return Race.TeamParticipation(
            team = teamId,
            riders = pcsTeamParticipation.riders.map {
                Race.RiderParticipation(
                    rider = it.rider.split("/").last(),
                    number = it.number.toIntOrNull(),
                )
            },
        )
    }

    private fun Doc.getCountry(): String =
        findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()
}
