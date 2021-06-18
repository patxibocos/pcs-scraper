import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.extract
import it.skrape.fetcher.skrape
import it.skrape.selects.Doc
import it.skrape.selects.and
import it.skrape.selects.html5.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val country: String,
    val bike: String,
    val jersey: String,
    val website: String,
    val year: Int,
    val riders: List<Rider>,
)

data class Rider(
    val id: String,
    val firstName: String,
    val lastName: String,
    val country: String,
    val website: String? = null,
    val birthDate: LocalDate,
    val birthPlace: String? = null,
    val weight: Int? = null,
    val height: Int? = null,
    val imageUrl: String,
)

fun parseTeamsDoc(teamsDoc: Doc): List<String> =
    teamsDoc.ul {
        withClass = "list" and "fs14" and "columns2" and "mob_columns1"
        findFirst { this }
    }.li {
        findAll {
            map {
                it.div {
                    a { findFirst { attribute("href") } }
                }
            }
        }
    }

fun parseTeamDoc(teamDoc: Doc, teamUrl: String, riderFetcher: (String, String) -> Rider): Team {
    val infoList = teamDoc.ul {
        withClass = "infolist"
        this
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
    val website = teamDoc.ul {
        withClass = "sites"
        this
    }.li {
        findFirst("span.website") { this }.parent.a {
            findFirst { attribute("href") }
        }
    }
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
    return Team(
        id = teamUrl,
        name = teamName,
        abbreviation = abbreviation,
        country = teamCountry,
        bike = bike,
        website = website,
        jersey = jersey,
        year = year,
        riders = scrapTeamRiders(teamDoc).map { (riderName, riderUrl) ->
            riderFetcher(riderName, riderUrl)
        },
    )
}

fun scrapTeamRiders(teamDoc: Doc): List<Pair<String, String>> =
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
                        riderName to riderUrl
                    }
                }

            }
        }
    }

fun parseRiderDoc(riderDoc: Doc, riderName: String, riderUrl: String): Rider =
    riderDoc.div {
        val (firstName, lastName) = getFirstAndLastName(riderName)
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
        val website = riderDoc.ul {
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
        val unparsedDate = infoContent.ownText.split(' ').dropLast(1)
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
        val birthDate = LocalDate.parse(unparsedDate.joinToString(" "), formatter)
        val birthPlaceWeightAndHeight = infoContent.children.last().findFirst { text }.split(' ')
        val birthPlaceWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("birth") }
        println("$firstName $lastName")
        val birthPlace = if (birthPlaceWordIndex != -1)
            birthPlaceWeightAndHeight[birthPlaceWordIndex + 1]
        else
            null
        val weightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("weight") }
        val weight =
            if (weightWordIndex != -1) birthPlaceWeightAndHeight[weightWordIndex + 1].toFloat().toInt() else null
        val heightWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("height") }
        val height =
            if (heightWordIndex != -1) (birthPlaceWeightAndHeight[heightWordIndex + 1].toFloat() * 100).toInt() else null
        val imageUrl = riderDoc.img {
            findFirst {
                attribute("src")
            }
        }
        Rider(
            id = riderUrl,
            firstName = firstName,
            lastName = lastName,
            country = country,
            website = website,
            birthDate = birthDate,
            birthPlace = birthPlace,
            weight = weight,
            height = height,
            imageUrl = imageUrl,
        )
    }

fun getFirstAndLastName(fullName: String): Pair<String, String> {
    val index = fullName.indexOfFirst { it.isLowerCase() } - 2
    val firstName = fullName.substring(index + 1, fullName.length)
    val lastName = fullName.substring(0, index).split(" ")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() }.trim() }
    return firstName to lastName
}

fun getDoc(docUrl: String, forceRemoteFetching: Boolean = false, relaxedParsing: Boolean = false): Doc {
    val localFile = File(docUrl)
    if (!forceRemoteFetching && localFile.exists()) {
        return htmlDocument(localFile) {
            relaxed = relaxedParsing
            return@htmlDocument this
        }
    }
    return skrape(HttpFetcher) {
        request {
            url = "https://www.procyclingstats.com/$docUrl"
        }
        extract {
            htmlDocument {
                localFile.absoluteFile.parentFile.mkdirs()
                localFile.writeText(this.document.html())
                relaxed = relaxedParsing
                return@htmlDocument this
            }
        }
    }
}

fun main() {
    val season = 2021
    val teamsDoc = getDoc("teams.php?year=$season&filter=Filter&s=worldtour")
    val teams = parseTeamsDoc(teamsDoc)
    teams.forEach { teamUrl ->
        val teamDoc = getDoc(teamUrl)
        parseTeamDoc(teamDoc, teamUrl) { riderName, riderUrl ->
            val riderDoc = getDoc(riderUrl, relaxedParsing = true)
            parseRiderDoc(riderDoc, riderName, riderUrl)
        }
    }
}