package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import it.skrape.selects.CssSelector
import it.skrape.selects.Doc
import it.skrape.selects.html5.a
import it.skrape.selects.html5.b
import it.skrape.selects.html5.div
import it.skrape.selects.html5.h1
import it.skrape.selects.html5.li
import it.skrape.selects.html5.span
import it.skrape.selects.html5.tbody
import it.skrape.selects.html5.td
import it.skrape.selects.html5.thead
import it.skrape.selects.html5.tr
import it.skrape.selects.html5.ul
import kotlinx.coroutines.coroutineScope
import java.net.URI
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class PCSScraper(private val docFetcher: DocFetcher, private val pcsUrl: String) :
    TeamsScraper,
    RidersScraper,
    RacesScraper {
    override suspend fun scrapeTeams(season: Int): List<Team> = coroutineScope {
        getTeamsUrls(season).map { teamUrl ->
            getTeam(teamUrl, season)
        }.map(::pcsTeamToTeam).sortedBy { it.name }
    }

    override suspend fun scrapeRiders(season: Int): List<Rider> = coroutineScope {
        val pcsTeams = getTeamsUrls(season).map { teamUrl ->
            getTeam(teamUrl, season)
        }
        val pcsRiders = pcsTeams
            .flatMap(PCSTeam::riders)
            .map { (riderUrl, riderFullName) -> getRider(riderUrl, riderFullName) }
            .distinctBy { it.url }
        val usCollator = Collator.getInstance(Locale.US)
        val ridersComparator = compareBy(usCollator) { r: Rider -> r.lastName.lowercase() }
            .thenBy(usCollator) { r: Rider -> r.firstName.lowercase() }
        pcsRiders.map(::pcsRiderToRider).sortedWith(ridersComparator)
    }

    override suspend fun scrapeRaces(): List<Race> = coroutineScope {
        getRacesUrls().mapNotNull { raceUrl ->
            getRace(raceUrl)
        }.map(::pcsRaceToRace).sortedBy { it.startDate }
    }

    private suspend fun getTeamsUrls(season: Int): List<String> {
        val teamsURL = buildURL("teams.php?year=$season&filter=Filter&s=worldtour")
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
        val website = teamDoc.getElementWebsite()

        fun getJerseyImageFromUci(): String {
            val uciCategory = when (status) {
                "WT" -> "WTT"
                "PRT" -> "PRT"
                else -> ""
            }
            return "https://ucibws.uci.ch/api/WebResources/ModulesData/Teams/2021/ROA/Jerseys/$uciCategory/ROA-${uciCategory}_${abbreviation}_$season.jpg"
        }

        val jersey = getJerseyImageFromUci()
        val pageTitleMain = teamDoc.findFirst(".page-title > .main")
        val teamName = pageTitleMain.h1 { findFirst { text } }.substringBefore('(').trim()
        val teamCountry = pageTitleMain.findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()
        val year = pageTitleMain.findLast("span").ownText.toInt()
        return PCSTeam(
            url = teamUrl,
            name = teamName,
            status = status,
            abbreviation = abbreviation,
            country = teamCountry,
            bike = bike,
            website = website,
            jersey = jersey,
            year = year,
            riders = getTeamRiders(teamDoc),
        )
    }

    private fun getTeamRiders(teamDoc: Doc): List<Pair<String, String>> =
        teamDoc.findAll(".ttabs.tabb a").map { it.attribute("href") to it.text }

    private suspend fun getRider(riderUrl: String, riderFullName: String): PCSRider {
        val riderURL = buildURL(riderUrl)
        val riderDoc = docFetcher.getDoc(riderURL) { relaxed = true }
        val infoContent = riderDoc.findFirst(".rdr-info-cont")
        val country = infoContent.findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()
        val website = riderDoc.getElementWebsite()
        val birthDate = infoContent.ownText.split(' ').take(3).joinToString(" ")
        val birthPlaceWeightAndHeight = infoContent.children.last().findFirst { text }.split(' ')
        val birthPlaceWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("birth") }
        val birthPlace = if (birthPlaceWordIndex != -1) birthPlaceWeightAndHeight[birthPlaceWordIndex + 1] else null
        val weightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("weight") }
        val weight = if (weightWordIndex != -1) birthPlaceWeightAndHeight[weightWordIndex + 1] else null
        val heightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("height") }
        val height = if (heightWordIndex != -1) birthPlaceWeightAndHeight[heightWordIndex + 1] else null
        val imageUrl = riderDoc.findFirst("img").attribute("src")
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
        )
    }

    private suspend fun getRacesUrls(): List<String> {
        val calendarUrl = buildURL("calendar/wt-calendar-chart")
        val calendarDoc = docFetcher.getDoc(calendarUrl)
        return calendarDoc.findAll("ul.gantt a").map { it.attribute("href") }
    }

    private suspend fun getRace(raceUrl: String): PCSRace? = coroutineScope {
        val raceURL = buildURL(raceUrl)
        val raceDoc = docFetcher.getDoc(raceURL) { relaxed = true }
        val infoList = raceDoc.ul { withClass = "infolist"; this }
        val uciTour = infoList.findByIndex(3, "li").findSecond("div").ownText
        // Discard any race that is not part of the UCI Worldtour
        if (uciTour != "UCI Worldtour") {
            return@coroutineScope null
        }
        val header = raceDoc.findAll(".page-topnav > ul > li")
        val participantsIndex = header.indexOfFirst { it.text == "Startlist" }
        val resultsIndex = header.indexOfFirst { it.text == "Results" }
        val stagesIndex = header.indexOfFirst { it.text.startsWith("Stages") }
        val raceParticipantsUrl = header[participantsIndex].a { findFirst { attribute("href") } }
        val raceResultUrl = header[resultsIndex].a { findFirst { attribute("href") } }
        val stagesUrl = header[stagesIndex].a { findFirst { attribute("href") } }
        val startDate = infoList.findFirst("li").findSecond("div").ownText
        val endDate = infoList.findSecond("li").findSecond("div").ownText
        val stages = if (startDate == endDate) {
            listOf(getStage(raceResultUrl))
        } else {
            getStages(stagesUrl)
        }

        val name = raceDoc.findFirst(".main > h1").text
        val country = raceDoc.findFirst("span.flag").classNames.find { it.length == 2 }.orEmpty()
        val websites = raceDoc.findAll("ul.list.circle.bluelink.fs14 a").map { it.attribute("href") }
        val website = websites.firstOrNull {
            !it.contains("twitter") && !it.contains("facebook") && !it.contains("instagram") && it.trim().isNotEmpty()
        }

        val startList = getRaceStartList(raceParticipantsUrl)
        val raceResultDoc = docFetcher.getDoc(buildURL(raceResultUrl)) { relaxed = true }
        val result = getResult(raceResultDoc)
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
        val startList = raceStartListDoc.ul {
            withClass = "startlist_v3"
            findAll("li.team")
        }.map {
            val team = it.b { a { findFirst { attribute("href") } } }
            val riders = it.ul {
                findAll("li")
            }.map { riderDocElement ->
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
        val stagesUrls = stagesDoc.findFirst("table.basic") {
            tbody {
                tr {
                    findAll {
                        map {
                            it.findThird("td").a {
                                findFirst { attribute("href") }
                            }
                        }
                    }
                }
            }
        }
        stagesUrls.map { stageUrl -> getStage(stageUrl) }
    }

    private suspend fun getStage(stageUrl: String): PCSStage {
        val stageURL = buildURL(stageUrl)
        val stageDoc = docFetcher.getDoc(stageURL) { relaxed = true }

        fun <T> findInInfoListByIndex(index: Int, init: CssSelector.() -> T): T {
            return stageDoc.ul {
                withClass = "infolist"
                li {
                    findByIndex(index) {
                        div {
                            this.init()
                        }
                    }
                }
            }
        }

        val startDateTime = findInInfoListByIndex(0) { findSecond { ownText } }
        val distance = findInInfoListByIndex(3) { findSecond { ownText } }
        val type = findInInfoListByIndex(5) { findSecond { findFirst("span") } }.classNames.last()
        val departure = findInInfoListByIndex(8) { findSecond { a { findFirst { this } }.text } }.ifEmpty { null }
        val arrival = findInInfoListByIndex(9) { findSecond { a { findFirst { this } }.text } }.ifEmpty { null }
        val result = getResult(stageDoc)
        return PCSStage(
            url = stageUrl,
            startDate = startDateTime,
            distance = distance,
            type = type,
            departure = departure,
            arrival = arrival,
            result = result,
        )
    }

    private fun getResult(doc: Doc): List<PCSRiderResult> {
        val resultsTable = doc.findFirst("div:not(.hide) > table.results")
        val resultColumns = resultsTable.thead { tr { findAll("th") } }
        val positionColumnIndex = resultColumns.indexOfFirst { it.ownText == "Rnk" }
        val riderColumnIndex = resultColumns.indexOfFirst { it.ownText == "Rider" }
        val timeColumnIndex = resultColumns.indexOfFirst { it.ownText == "Time" }
        return resultsTable.tbody {
            tr {
                findAll {
                    map {
                        val position = it.td { findByIndex(positionColumnIndex) }.ownText
                        val rider = it.td { findByIndex(riderColumnIndex) }.a { findFirst { attribute("href") } }
                        val time = it.td { findByIndex(timeColumnIndex) }.ownText.ifEmpty {
                            it.td { findByIndex(timeColumnIndex) }.span { findFirst { ownText } }
                        }
                        PCSRiderResult(
                            position = position,
                            rider = rider,
                            time = time,
                        )
                    }
                }
            }
        }
    }

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
            riders = pcsTeam.riders.map(Pair<String, String>::first).map { it.split("/").last() },
        )

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
        )
    }

    private fun pcsRaceToRace(pcsRace: PCSRace): Race {
        val raceId = pcsRace.url.split("/").dropLast(1).takeLast(2).joinToString("/").replace("/", "-")
        return Race(
            id = raceId,
            name = pcsRace.name,
            country = pcsRace.country.uppercase(),
            startDate = LocalDate.parse(pcsRace.startDate, DateTimeFormatter.ISO_LOCAL_DATE),
            endDate = LocalDate.parse(pcsRace.endDate, DateTimeFormatter.ISO_LOCAL_DATE),
            website = pcsRace.website,
            stages = pcsRace.stages.map { pcsStageToStage(it) },
            startList = pcsRace.startList.map { pcsTeamParticipationToTeamParticipation(it) },
            result = pcsRiderResultToRiderResult(pcsRace.result)
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun pcsRiderResultToRiderResult(pcsRiderResults: List<PCSRiderResult>): List<Race.RiderResult> {
        var currentTime = 0L
        return pcsRiderResults.take(10).mapNotNull {
            val rider = it.rider.split("/").last()
            if (it.position.toIntOrNull() == null) { // Riders that didn't finish have a position which is not a number
                return@mapNotNull null
            }
            if (it.time != ",,") { // Time being ,, means that it has the same time as the previous rider
                val parts = it.time.split(":").map(String::toInt)
                val timeInSeconds = when (parts.size) {
                    3 -> {
                        val (hours, minutes, seconds) = parts
                        (Duration.hours(hours) + Duration.minutes(minutes) + Duration.seconds(seconds)).inWholeSeconds
                    }
                    2 -> {
                        val (minutes, seconds) = parts
                        (Duration.minutes(minutes) + Duration.seconds(seconds)).inWholeSeconds
                    }
                    else -> throw RuntimeException("Unexpected time value: ${it.time}")
                }
                currentTime += timeInSeconds
            }
            Race.RiderResult(it.position.toInt(), rider, currentTime)
        }
    }

    private fun pcsStageToStage(pcsStage: PCSStage): Race.Stage {
        // Some dates include time, so for now we just ignore the time part
        val startDate = pcsStage.startDate.replace(",", "").split(" ").take(3).joinToString(" ")
        // p1, p2, p3, p4 and p5 are the only valid values
        val pcsTypeIndex = (1..5).map { "p$it" }.indexOf(pcsStage.type).takeIf { it != -1 }
        val stageId = pcsStage.url.split("/")
            .takeLast(3)
            .joinToString("/")
            .replace("/", "-")
            .replace("result", "stage-1") // For single day races where stage info is on the result page
        val result = pcsRiderResultToRiderResult(pcsStage.result)
        return Race.Stage(
            id = stageId,
            startDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            distance = pcsStage.distance.split(" ").first().toFloat(),
            type = pcsTypeIndex?.let { Race.Stage.Type.values()[pcsTypeIndex] },
            departure = pcsStage.departure,
            arrival = pcsStage.arrival,
            result = result,
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

    private fun Doc.getElementWebsite(): String? =
        findFirst(".sites .website").takeIf {
            it.parents.isNotEmpty()
        }?.parent?.findFirst("a")?.attribute("href")

    private fun buildURL(path: String): URL =
        URI(pcsUrl).resolve("/").resolve(path).toURL()
}

private data class PCSTeam(
    val url: String,
    val name: String,
    val status: String,
    val abbreviation: String,
    val country: String,
    val bike: String,
    val jersey: String,
    val website: String? = null,
    val year: Int,
    val riders: List<Pair<String, String>>,
)

private data class PCSRider(
    val url: String,
    val fullName: String,
    val country: String,
    val website: String? = null,
    val birthDate: String,
    val birthPlace: String? = null,
    val weight: String? = null,
    val height: String? = null,
    val photo: String,
) {
    fun getFirstAndLastName(fullName: String): Pair<String, String> {
        val index = max(fullName.indexOfFirst { it.isLowerCase() } - 2, fullName.indexOfFirst { it.isWhitespace() })
        val firstName = fullName.substring(index + 1, fullName.length)
        val lastName = fullName.substring(0, index).split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() }.trim() }
        return firstName to lastName
    }
}

private data class PCSRace(
    val url: String,
    val name: String,
    val country: String,
    val startDate: String,
    val endDate: String,
    val website: String?,
    val stages: List<PCSStage>,
    val startList: List<PCSTeamParticipation>,
    val result: List<PCSRiderResult>,
)

private data class PCSTeamParticipation(
    val team: String,
    val riders: List<PCSRiderParticipation>,
)

private data class PCSRiderParticipation(
    val rider: String,
    val number: String,
)

private data class PCSStage(
    val url: String,
    val startDate: String,
    val distance: String,
    val type: String,
    val departure: String?,
    val arrival: String?,
    val result: List<PCSRiderResult>
)

private class PCSRiderResult(
    val position: String,
    val rider: String,
    val time: String,
)
