import it.skrape.selects.Doc
import it.skrape.selects.and
import it.skrape.selects.html5.*
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max

class PCSParser(private val pcsUrl: String) {

    data class PCSTeam(
        val url: String,
        val name: String,
        val abbreviation: String,
        val country: String,
        val bike: String,
        val jerseyUrl: String,
        val website: String? = null,
        val year: Int,
        val riders: List<Pair<String, String>>,
    )

    data class PCSRider(
        val url: String,
        val fullName: String,
        val country: String,
        val website: String? = null,
        val birthDate: String,
        val birthPlace: String? = null,
        val weight: String? = null,
        val height: String? = null,
        val photoUrl: String,
    ) {

        fun getFirstAndLastName(fullName: String): Pair<String, String> {
            val index = max(fullName.indexOfFirst { it.isLowerCase() } - 2, fullName.indexOfFirst { it.isWhitespace() })
            val firstName = fullName.substring(index + 1, fullName.length)
            val lastName = fullName.substring(0, index).split(" ")
                .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() }.trim() }
            return firstName to lastName
        }

    }

    fun pcsTeamToTeam(pcsTeam: PCSTeam): Team =
        Team(
            id = pcsTeam.url.split("/").last(),
            name = pcsTeam.name,
            abbreviation = pcsTeam.abbreviation,
            country = pcsTeam.country.uppercase(),
            bike = pcsTeam.bike,
            jerseyUrl = URI(this.pcsUrl).resolve(pcsTeam.jerseyUrl).toString(),
            website = pcsTeam.website,
            year = pcsTeam.year,
            riders = pcsTeam.riders.map(Pair<String, String>::first).map { it.split("/").last() }
        )

    fun pcsRiderToRider(pcsRider: PCSRider): Rider {
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
            photoUrl = URI(this.pcsUrl).resolve(pcsRider.photoUrl).toString(),
        )
    }

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

    fun parseTeamDoc(teamDoc: Doc): PCSTeam {
        val teamUrl = teamDoc.getDocUrl()
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
            abbreviation = abbreviation,
            country = teamCountry,
            bike = bike,
            website = website,
            jerseyUrl = jersey,
            year = year,
            riders = parseTeamRiders(teamDoc),
        )
    }

    private fun parseTeamRiders(teamDoc: Doc): List<Pair<String, String>> =
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

    fun parseRiderDoc(riderDoc: Doc, riderFullName: String): PCSRider {
        val riderUrl = riderDoc.getDocUrl()
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
            photoUrl = imageUrl,
        )
    }

    private fun Doc.getDocUrl(): String =
        this.div {
            withClass = "page-topnav"
            findFirst {
                ul {
                    findFirst {
                        li {
                            a {
                                findFirst {
                                    attribute("href")
                                }
                            }
                        }
                    }
                }
            }
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

}