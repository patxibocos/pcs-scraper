package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import it.skrape.selects.Doc
import it.skrape.selects.and
import it.skrape.selects.html5.a
import it.skrape.selects.html5.div
import it.skrape.selects.html5.h1
import it.skrape.selects.html5.h3
import it.skrape.selects.html5.img
import it.skrape.selects.html5.li
import it.skrape.selects.html5.span
import it.skrape.selects.html5.ul
import java.net.URI
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class PCSScraper(private val docFetcher: DocFetcher, private val pcsUrl: String) :
    TeamsScraper,
    RidersScraper,
    RacesScraper {
    override fun scrapeTeams(season: Int): List<Team> =
        getTeamsUrls(season).map(::getTeam).map(::pcsTeamToTeam).sortedBy { it.name }

    override fun scrapeRiders(season: Int): List<Rider> {
        val pcsTeams = getTeamsUrls(season).map(::getTeam)
        val pcsRiders = pcsTeams
            .flatMap(PCSTeam::riders)
            .map { (riderUrl, riderFullName) -> getRider(riderUrl, riderFullName) }
            .distinctBy { it.url }
        val usCollator = Collator.getInstance(Locale.US)
        val ridersComparator = compareBy(usCollator) { r: Rider -> r.lastName.lowercase() }
            .thenBy(usCollator) { r: Rider -> r.firstName.lowercase() }
        return pcsRiders.map(::pcsRiderToRider).sortedWith(ridersComparator)
    }

    override fun scrapeRaces(): List<Race> =
        getRacesUrls().map(::getRace).map(::pcsRaceToRace).sortedBy { it.startDate }

    private fun getTeamsUrls(season: Int): List<String> {
        val teamsURL = buildURL("teams.php?year=$season&filter=Filter&s=worldtour")
        val teamsDoc = docFetcher.getDoc(teamsURL)
        return teamsDoc.ul {
            withClass = "list" and "fs14" and "columns2" and "mob_columns1"
            findAll { this }
        }.flatMap { teamDocElement ->
            teamDocElement.li {
                findAll {
                    map {
                        it.div {
                            a { findFirst { attribute("href") } }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    private fun getTeam(teamUrl: String): PCSTeam {
        val teamURL = buildURL(teamUrl)
        val teamDoc = docFetcher.getDoc(teamURL) { relaxed = true }
        val infoList = teamDoc.ul {
            withClass = "infolist"
            this
        }
        val status = infoList.li {
            findFirst {
                div {
                    findSecond { ownText }
                }
            }
        }
        val abbreviation = infoList.li {
            findSecond {
                div {
                    findSecond { ownText }
                }
            }
        }
        val bike = infoList.li {
            findThird {
                div {
                    a {
                        findFirst { ownText }
                    }
                }
            }
        }
        val website = teamDoc.getElementWebsite()
        val jersey = infoList.li {
            findLast {
                img {
                    findFirst {
                        attribute("src")
                    }
                }
            }
        }
        val pageTitleMain = teamDoc.div {
            withClass = "page-title"
            div {
                withClass = "main"
                findFirst { this }
            }
        }
        val teamName = pageTitleMain.h1 {
            findFirst { text }
        }.substringBefore('(').trim()
        val teamCountry = pageTitleMain.span {
            withClass = "flag"
            findFirst {
                this.classNames.find { it.length == 2 }.orEmpty()
            }
        }.uppercase()
        val year = pageTitleMain.findLast("span") {
            findFirst {
                ownText
            }
        }.toInt()
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
        teamDoc.div {
            withClass = "ttabs" and "tabb"
            ul {
                li {
                    findAll {
                        map {
                            val riderInfo = it.div { findSecond { this } }
                            val riderLink = riderInfo.a { findFirst { this } }
                            val riderName = riderLink.text
                            val riderUrl = riderLink.attribute("href")
                            riderUrl to riderName
                        }
                    }
                }
            }
        }

    private fun getRider(riderUrl: String, riderFullName: String): PCSRider {
        val riderURL = buildURL(riderUrl)
        val riderDoc = docFetcher.getDoc(riderURL) { relaxed = true }
        val infoContent = riderDoc.div {
            withClass = "rdr-info-cont"
            findFirst { this }
        }
        val country = infoContent.span {
            withClass = "flag"
            findFirst {
                this.classNames.find { it.length == 2 }.orEmpty()
            }
        }
        val website = riderDoc.getElementWebsite()
        val birthDate = infoContent.ownText.split(' ').dropLast(1).joinToString(" ")
        val birthPlaceWeightAndHeight = infoContent.children.last().findFirst { text }.split(' ')
        val birthPlaceWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("birth") }
        val birthPlace = if (birthPlaceWordIndex != -1) birthPlaceWeightAndHeight[birthPlaceWordIndex + 1] else null
        val weightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("weight") }
        val weight = if (weightWordIndex != -1) birthPlaceWeightAndHeight[weightWordIndex + 1] else null
        val heightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("height") }
        val height = if (heightWordIndex != -1) birthPlaceWeightAndHeight[heightWordIndex + 1] else null
        val imageUrl = riderDoc.img {
            findFirst {
                attribute("src")
            }
        }
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

    private fun getRacesUrls(): List<String> {
        val calendarUrl = buildURL("calendar/wt-calendar-chart")
        val calendarDoc = docFetcher.getDoc(calendarUrl)
        return calendarDoc.ul {
            withClass = "gantt"
            li {
                findAll {
                    map {
                        val raceLink = it.a { findFirst { this } }
                        val raceUrl = raceLink.attribute("href")
                        raceUrl
                    }
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    private fun getRace(raceUrl: String): PCSRace {
        val raceURL = buildURL(raceUrl)
        val raceDoc = docFetcher.getDoc(raceURL) { relaxed = true }
        val h3 = raceDoc.h3 {
            findAll {
                this
            }
        }
        val stagesH3 = h3.find { it.text == "Stages" }
        val stagesUrls = stagesH3?.siblings?.first()?.li {
            findAll {
                map {
                    findThird {
                        div {
                            it.a {
                                findFirst { attribute("href") }
                            }
                        }
                    }
                }
            }
        }?.filter {
            // Remove rest days which contain a href pointing to the race url
            it.trimEnd('/') != raceUrl.split("/").dropLast(1).joinToString("/")
        } ?: emptyList()
        val stages = stagesUrls.map(::getStage)
        val infoList = raceDoc.ul {
            withClass = "infolist"
            this
        }
        val startDate = infoList.li {
            findFirst {
                div {
                    findSecond { ownText }
                }
            }
        }
        val endDate = infoList.li {
            findSecond {
                div {
                    findSecond { ownText }
                }
            }
        }
        val name = raceDoc.div {
            withClass = "main"
            h1 { findFirst { text } }
        }
        val websites = raceDoc.ul {
            withClass = "list" and "circle" and "bluelink" and "fs14"
            li {
                findAll {
                    map {
                        it.a {
                            findFirst { attribute("href") }
                        }
                    }
                }
            }
        }
        val website = websites.firstOrNull {
            !it.contains("twitter") && !it.contains("facebook") && !it.contains("instagram") && it.trim().isNotEmpty()
        }
        return PCSRace(
            url = raceUrl,
            name = name,
            startDate = startDate,
            endDate = endDate,
            website = website,
            stages = stages,
        )
    }

    @Suppress("DuplicatedCode")
    private fun getStage(stageUrl: String): PCSStage {
        val stageURL = buildURL(stageUrl)
        val stageDoc = docFetcher.getDoc(stageURL) { relaxed = true }
        val infoList = stageDoc.ul {
            withClass = "infolist"
            this
        }
        val startDateTime = infoList.li {
            findFirst {
                div {
                    findSecond { ownText }
                }
            }
        }
        return PCSStage(
            url = stageUrl,
            startDate = startDateTime,
        )
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
        val birthDate = LocalDate.parse(pcsRider.birthDate, dateFormatter)
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

    private fun pcsRaceToRace(pcsRace: PCSRace): Race =
        Race(
            id = pcsRace.url.split("/").dropLast(1).takeLast(2).joinToString("/"),
            name = pcsRace.name,
            startDate = LocalDate.parse(pcsRace.startDate, DateTimeFormatter.ISO_LOCAL_DATE),
            endDate = LocalDate.parse(pcsRace.endDate, DateTimeFormatter.ISO_LOCAL_DATE),
            website = pcsRace.website,
            stages = pcsRace.stages.map(::pcsStageToStage)
        )

    private fun pcsStageToStage(pcsStage: PCSStage): Race.Stage {
        // Some dates include time, so for now we just ignore the time part
        val startDate = pcsStage.startDate.replace(",", "").split(" ").take(3).joinToString(" ")
        return Race.Stage(
            id = pcsStage.url.split("/").last(),
            startDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        )
    }

    private fun Doc.getElementWebsite(): String? =
        this.ul {
            withClass = "sites"
            this
        }.li {
            val websiteElements = findAll("span.website")
            if (websiteElements.isEmpty()) {
                return@li null
            }
            websiteElements.first().parent.a {
                findFirst { attribute("href") }
            }
        }

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
    val startDate: String,
    val endDate: String,
    val website: String?,
    val stages: List<PCSStage>
)

private data class PCSStage(
    val url: String,
    val startDate: String
)
