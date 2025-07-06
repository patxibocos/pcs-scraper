package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Race
import it.skrape.selects.Doc
import it.skrape.selects.DocElement
import it.skrape.selects.html5.*
import it.skrape.selects.text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.slf4j.Logger
import java.net.URI
import java.net.URL
import java.time.*
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

    override suspend fun scrapeRaces(
        season: Int,
        teamIdMapper: (String) -> String?,
    ): List<Race> = coroutineScope {
        logger.info("Scraping races for $season season")
        val pcsRaces = getRacesUrls(season).map { raceUrl -> async { getRace(raceUrl) } }.awaitAll()
        pcsRaces.map { pcsRaceToRace(it, teamIdMapper) }.sortedBy { it.stages.first().startDateTime }
    }

    private suspend fun getRacesUrls(season: Int): List<String> {
        val calendarUrl = buildURL("races.php?season=$season&category=1&s=calendar-plus-filters")
        val calendarDoc = docFetcher.getDoc(calendarUrl)
        return calendarDoc.findAll("table tr:not(.striked) > td:nth-child(2) > a").map { it.attribute("href") }
            .map { it.substring(0, it.lastIndexOf("/$season")) + "/$season" }
    }

    private suspend fun getRace(raceUrl: String): PCSRace = coroutineScope {
        val raceURL = buildURL(raceUrl)
        val raceDoc = docFetcher.getDoc(raceURL) { relaxed = true }
        val raceParticipantsUrl = raceDoc.findAll("a").first { it.text == "Startlist" }.attribute("href")
        val startDate = raceDoc.findNextSiblingElements { text == "Startdate:" }.firstOrNull()?.text
        val endDate = raceDoc.findNextSiblingElements { text == "Enddate:" }.firstOrNull()?.text
        val name = raceDoc.findFirst(".thumbnav > span > a").ownText
        val stages = if (startDate == endDate) {
            listOf(getStage("$raceUrl/result", true))
        } else {
            logger.info("Scraping stages for race $name")
            val stagesTable =
                raceDoc.findNextSiblingElements { ownText == "Stages" && parent.classNames.contains("mt20") }.first()
            getStages(stagesTable)
        }
        val country = raceDoc.getCountry()
        val websites = raceDoc.findAll("ul.list.circle.bluelink.fs14 a").map { it.attribute("href") }
        val website = websites.firstOrNull {
            !it.contains("twitter") && !it.contains("facebook") && !it.contains("instagram") && it.trim()
                .isNotEmpty()
        }
        val startList = getRaceStartList(raceParticipantsUrl)
        PCSRace(
            url = raceUrl,
            name = name,
            country = country,
            website = website,
            stages = stages,
            startList = startList,
        )
    }

    private suspend fun getRaceStartList(raceStartListUrl: String): List<PCSTeamParticipation> {
        val raceParticipantsUrl = buildURL(raceStartListUrl)
        val raceStartListDoc = docFetcher.getDoc(raceParticipantsUrl) { relaxed = true }
        val startList = raceStartListDoc.findAll("ul.startlist_v4 > li").map {
            val team = it.findFirst("a").attribute("href")
            val riders = it.findAll("ul > li").map { riderDocElement ->
                PCSRiderParticipation(
                    rider = riderDocElement.a { findFirst { attribute("href") } },
                    number = riderDocElement.findFirst("span").text,
                )
            }
            PCSTeamParticipation(
                team = team,
                riders = riders,
            )
        }
        return startList
    }

    private suspend fun getStages(stagesTable: DocElement): List<PCSStage> = coroutineScope {
        val stagesUrls =
            stagesTable.findAll("tbody > tr:not(.sum)").filter { it.findByIndex(3, "td").text != "Restday" }
                .map {
                    val stageTypeTd = it.findByIndex(2, "td")
                    val stageLink = it.findByIndex(3, "td")
                    val stageUrl = stageLink.a { findFirst { attribute("href") } }
                    val stageType =
                        stageTypeTd.children.first().classNames.first { classNames -> classNames.length == 2 }
                    stageUrl to stageType
                }
        stagesUrls.map { (stageUrl, stageType) -> async { getStage(stageUrl, false, stageType) } }.awaitAll()
    }

    private suspend fun getStage(stageUrl: String, isSingleDayRace: Boolean, type: String? = null): PCSStage =
        coroutineScope {
            val stageURL = buildURL(stageUrl)
            val stageDoc = docFetcher.getDoc(stageURL) { relaxed = true }
            val startDate = stageDoc.findNextSiblingElements { text == "Datename:" }.text
            val startTime = stageDoc.findNextSiblingElements { text == "Start time:" }.text
            val startTimeCET = if (startTime.isNotBlank() && startTime != "-") {
                val cetTimePart = startTime.substring(startTime.indexOf('(') + 1)
                cetTimePart.substring(0, 5)
            } else {
                null
            }
            val distance = stageDoc.findNextSiblingElements { text == "Distance:" }.text
            val type = type ?: stageDoc.findNextSiblingElements { ownText == "Parcours type:" }
                .first().children.first().classNames.toList()[2]
            val stageTitle = stageDoc.findFirst(".title-line2").text
            val isIndividualTimeTrial = stageTitle.contains("ITT")
            val isTeamTimeTrial = stageTitle.contains("TTT")
            val departure = stageDoc.findNextSiblingElements { ownText == "Departure:" }.text
            val arrival = stageDoc.findNextSiblingElements { ownText == "Arrival:" }.text
            var stageTimeResult: List<PCSParticipantResult> = emptyList()
            var stageYouthResult: List<PCSParticipantResult> = emptyList()
            var stageTeamsResult: List<PCSParticipantResult> = emptyList()
            var stageKomResult: List<PCSPlaceResult> = emptyList()
            var stagePointsResult: List<PCSPlaceResult> = emptyList()
            var generalTimeResult: List<PCSParticipantResult> = emptyList()
            var generalYouthResult: List<PCSParticipantResult> = emptyList()
            var generalTeamsResult: List<PCSParticipantResult> = emptyList()
            var generalKomResult: List<PCSParticipantResult> = emptyList()
            var generalPointsResult: List<PCSParticipantResult> = emptyList()
            val results = stageDoc.findFirst("#resultsCont").children
            when (isSingleDayRace) {
                // Single day races will only have stage result
                true -> {
                    if (results.isNotEmpty()) {
                        stageTimeResult = getParticipantResult(
                            table = results.first().findFirst("table"),
                            resultType = ParticipantResultType.TIME
                        )
                        generalTimeResult = stageTimeResult
                    }
                }

                false -> {
                    stageDoc.ul(".tabs.tabnav") { findAll("li") }.map { it.text }.forEachIndexed { index, ranking ->
                        when (ranking.lowercase()) {
                            "stage" ->
                                stageTimeResult =
                                    getParticipantResult(
                                        table = results[index].findFirst("table"),
                                        isTTT = isTeamTimeTrial,
                                        resultType = ParticipantResultType.TIME,
                                    )

                            "gc" -> generalTimeResult = getParticipantResult(
                                table = results[index].findFirst("table"),
                                resultType = ParticipantResultType.TIME
                            )

                            "points" -> {
                                stagePointsResult = getPointsPerPlaceResult(results[index].findFirst(".today"))
                                generalPointsResult = getParticipantResult(
                                    table = results[index].findFirst("table"),
                                    resultType = ParticipantResultType.POINTS
                                )
                            }

                            "kom" -> {
                                stageKomResult =
                                    getPointsPerPlaceResult(results[index].findFirst(".today"))
                                generalKomResult = getParticipantResult(
                                    table = results[index].findFirst("table"),
                                    resultType = ParticipantResultType.POINTS
                                )
                            }

                            "youth" -> {
                                stageYouthResult = getParticipantResult(
                                    table = results[index].findSecond("table"),
                                    resultType = ParticipantResultType.TIME
                                )
                                generalYouthResult = getParticipantResult(
                                    table = results[index].findFirst("table"),
                                    resultType = ParticipantResultType.TIME
                                )
                            }

                            "teams" -> {
                                stageTeamsResult = getParticipantResult(
                                    table = results[index].findSecond("table"),
                                    resultType = ParticipantResultType.TIME
                                )
                                generalTeamsResult = getParticipantResult(
                                    table = results[index].findFirst("table"),
                                    resultType = ParticipantResultType.TIME
                                )
                            }
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
                stageTimeResult = stageTimeResult,
                stageYouthResult = stageYouthResult,
                stageTeamsResult = stageTeamsResult,
                stageKomResult = stageKomResult,
                stagePointsResult = stagePointsResult,
                generalTimeResult = generalTimeResult,
                generalYouthResult = generalYouthResult,
                generalTeamsResult = generalTeamsResult,
                generalKomResult = generalKomResult,
                generalPointsResult = generalPointsResult,
            )
        }

    private fun getPointsPerPlaceResult(results: DocElement): List<PCSPlaceResult> {
        val placeNames = results.findAll("h4")
        return placeNames.mapIndexed { index, element ->
            PCSPlaceResult(
                place = PointsPlace(title = element.text),
                result = getParticipantResult(
                    table = results.findByIndex(index, "table"),
                    resultType = ParticipantResultType.POINTS
                ),
            )
        }
    }

    enum class ParticipantResultType(val names: List<String>) {
        TIME(listOf("Time")),
        POINTS(listOf("Points", "Pnt"))
    }

    private fun getParticipantResult(
        table: DocElement,
        isTTT: Boolean = false,
        resultType: ParticipantResultType,

        ): List<PCSParticipantResult> {
        if (table.text.isEmpty()) {
            return emptyList()
        }
        val resultColumns = table.thead { findAll("th") }
        val positionColumnIndex =
            resultColumns.indexOfFirst { it.ownText == "Pos." || it.ownText == "Rnk" || it.ownText == "#" }
        val participantColumnIndex = resultColumns.indexOfFirst { it.ownText == "Rider" || it.ownText == "Team" }
        val timeOrPointsColumnIndex = resultColumns.indexOfFirst { it.ownText in resultType.names }
        if (timeOrPointsColumnIndex == -1) {
            // Some results sometimes miss points/time, so we just skip them
            return emptyList()
        }
        return table.findAll("tbody > tr".plus(if (isTTT) ".team" else "")).mapNotNull {
            // This workaround is because sometimes there are info rows that are not results themselves
            // i.e.
            // <tr>
            //   <td></td>
            //   <td colspan="23" style="padding: 2px; font-size: 10px; color: #999;">Michael Matthews relegated from 3rd to 11th </td>
            //  </tr>
            if (it.children.size <= 2) {
                return@mapNotNull null
            }
            val position = it.td { findByIndex(positionColumnIndex) }.ownText
            val participantTd = it.td { findByIndex(participantColumnIndex) }
            val participant = participantTd.a { findFirst { attribute("href") } }
            val name = participantTd.a { findFirst { text } }
            if (name.isEmpty()) {
                return@mapNotNull null
            }
            val timeOrPoints = it.td { findByIndex(timeOrPointsColumnIndex) }.ownText.ifEmpty {
                it.td { findByIndex(timeOrPointsColumnIndex) }.span { findFirst { ownText } }
            }.ifEmpty {
                // This gets the points instead of the time
                it.td { findByIndex(timeOrPointsColumnIndex) }.text
            }
            PCSParticipantResult(
                position = position,
                participant = participant,
                result = timeOrPoints,
                name = name,
            )
        }
    }

    private fun pcsRaceToRace(
        pcsRace: PCSRace,
        teamIdMapper: (String) -> String?,
    ): Race {
        val raceId = pcsRace.url.split("/").takeLast(2).joinToString("-")
        // TODO clear stageResults if generalResults.time is not available
        return Race(
            id = raceId,
            name = pcsRace.name,
            country = pcsRace.country.uppercase(),
            website = pcsRace.website,
            stages = pcsRace.stages.map { pcsStageToStage(it, teamIdMapper) },
            startList = pcsRace.startList.mapNotNull {
                pcsTeamParticipationToTeamParticipation(
                    it,
                    teamIdMapper,
                )
            },
        )
    }

    private fun pcsParticipantResultToTeamResult(
        pcsParticipantResults: List<PCSParticipantResult>,
        teamIdMapper: (String) -> String?,
    ): List<Race.ParticipantResultTime> {
        if (pcsParticipantResults.isEmpty()) {
            return emptyList()
        }
        return pcsParticipantResults.take(10).mapNotNull {
            val position = it.position.toInt()
            val team = teamIdMapper(it.participant.split("/").last()) ?: return@mapNotNull null
            val (minutes, seconds) = it.result.split(":", ".").map(String::toInt)
            val time = (minutes.minutes + seconds.seconds).inWholeSeconds
            Race.ParticipantResultTime(position, team, time, it.name)
        }
    }

    private fun pcsParticipantResultToRiderResultPoints(
        pcsParticipantResults: List<PCSParticipantResult>,
    ): List<Race.ParticipantResultPoints> {
        if (pcsParticipantResults.isEmpty()) {
            return emptyList()
        }
        return pcsParticipantResults.take(10).mapNotNull {
            val rider = it.participant.split("/").last()
            if (it.position.toIntOrNull() == null) { // Riders that didn't finish have a position which is not a number
                return@mapNotNull null
            }
            // result, which happens for TTT where there may have no points
            if (it.result.isEmpty()) {
                return@mapNotNull null
            }
            Race.ParticipantResultPoints(it.position.toInt(), rider, it.result.toInt(), it.name)
        }
    }

    private fun pcsParticipantResultToRiderResultTime(
        pcsParticipantResults: List<PCSParticipantResult>,
        idMapper: (String) -> String? = { it },
    ): List<Race.ParticipantResultTime> {
        if (pcsParticipantResults.isEmpty()) {
            return emptyList()
        }
        var firstRiderTime = 0L
        var previousDiff = 0L
        return pcsParticipantResults.take(10).mapNotNull {
            val participant = idMapper(it.participant.split("/").last()) ?: return@mapNotNull null
            if (it.position.toIntOrNull() == null) { // Riders that didn't finish have a position which is not a number
                return@mapNotNull null
            }
            if (it.result != ",,") { // Time being ,, means that it has the same time as the previous rider
                val splits = it.result.split(".", ":") // The delimiter is not always the same
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

                    else -> throw RuntimeException("Unexpected time value: ${it.result}")
                }
                if (firstRiderTime == 0L) {
                    firstRiderTime = timeInSeconds
                } else {
                    previousDiff = timeInSeconds
                }
            }
            Race.ParticipantResultTime(it.position.toInt(), participant, firstRiderTime + previousDiff, it.name)
        }
    }

    private fun pcsPlaceResultToPlaceResult(
        pcsPlaceResults: List<PCSPlaceResult>,
        stageDistance: Float,
    ): List<Race.PlaceResult> {
        return pcsPlaceResults.map {
            val placeName = it.place.title
            val name = when {
                placeName.contains("|") -> placeName.substring(placeName.indexOf("|") + 2, placeName.lastIndexOf("("))
                placeName.indexOf("(") == -1 -> "Finish"
                placeName.indexOf("(") == placeName.lastIndexOf("(") -> placeName.substring(0, placeName.indexOf("("))
                else -> placeName.substring(placeName.indexOf(")") + 1, placeName.lastIndexOf("(") - 1).trim()
            }
            val distance = if (placeName.endsWith(")")) {
                var index = placeName.lastIndexOf(") km)")
                if (index == -1) {
                    index = placeName.lastIndexOf("km)")
                }
                placeName.substring(placeName.lastIndexOf("(") + 1, index).trim().toFloat()
            } else {
                stageDistance
            }
            Race.PlaceResult(
                place = Race.Place(name = name.trim(), distance = distance),
                points = pcsParticipantResultToRiderResultPoints(it.result),
            )
        }
    }

    private fun pcsStageToStage(
        pcsStage: PCSStage,
        teamIdMapper: (String) -> String?,
    ): Race.Stage {
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
        val stageTimeResult = if (pcsStage.teamTimeTrial) {
            pcsParticipantResultToTeamResult(pcsStage.stageTimeResult, teamIdMapper)
        } else {
            pcsParticipantResultToRiderResultTime(pcsStage.stageTimeResult)
        }.takeIf { it.size >= 3 }.orEmpty()
        val distance = pcsStage.distance.split(" ").first().toFloat()
        val stageYouthResult = pcsParticipantResultToRiderResultTime(pcsStage.stageYouthResult)
        val stageTeamsResult = pcsParticipantResultToRiderResultTime(pcsStage.stageTeamsResult, teamIdMapper)
        val stageKomResult = pcsPlaceResultToPlaceResult(pcsStage.stageKomResult, distance)
        val stagePointsResult = pcsPlaceResultToPlaceResult(pcsStage.stagePointsResult, distance)
        val generalTimeResult =
            pcsParticipantResultToRiderResultTime(pcsStage.generalTimeResult).takeIf { it.size >= 3 }
                .orEmpty()
        val generalYouthResult = pcsParticipantResultToRiderResultTime(pcsStage.generalYouthResult)
        val generalTeamsResult = pcsParticipantResultToRiderResultTime(pcsStage.generalTeamsResult, teamIdMapper)
        val generalKomResult = pcsParticipantResultToRiderResultPoints(pcsStage.generalKomResult)
        val generalPointsResult = pcsParticipantResultToRiderResultPoints(pcsStage.generalPointsResult)
        return Race.Stage(
            id = stageId,
            startDateTime = startDateTime,
            distance = distance,
            profileType = pcsTypeIndex?.let { Race.Stage.ProfileType.entries[pcsTypeIndex] },
            departure = pcsStage.departure,
            arrival = pcsStage.arrival,
            stageType = when {
                pcsStage.individualTimeTrial -> Race.Stage.StageType.INDIVIDUAL_TIME_TRIAL
                pcsStage.teamTimeTrial -> Race.Stage.StageType.TEAM_TIME_TRIAL
                else -> Race.Stage.StageType.REGULAR
            },
            stageResults = Race.StageResults(
                time = stageTimeResult,
                youth = stageYouthResult,
                teams = stageTeamsResult,
                kom = stageKomResult,
                points = stagePointsResult,
            ),
            generalResults = Race.GeneralResults(
                time = generalTimeResult,
                youth = generalYouthResult,
                teams = generalTeamsResult,
                kom = generalKomResult,
                points = generalPointsResult,
            ),
        )
    }

    private fun pcsTeamParticipationToTeamParticipation(
        pcsTeamParticipation: PCSTeamParticipation,
        teamIdMapper: (String) -> String?,
    ): Race.TeamParticipation? {
        val teamId = teamIdMapper(pcsTeamParticipation.team.split("/").last()) ?: return null
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
        findFirst(".title span.flag").classNames.find { it.length == 2 }.orEmpty()

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()
}
