import it.skrape.selects.Doc
import it.skrape.selects.and
import it.skrape.selects.html5.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

class Scrapper {
    data class Team(
        val id: String,
        val name: String,
        val abbreviation: String,
        val country: String,
        val bike: String,
        val jersey: String,
        val website: String? = null,
        val year: Int,
        val riders: List<TeamRider>,
    )

    data class TeamRider(
        val id: String,
        val fullName: String,
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

    fun parseTeamDoc(teamDoc: Doc): Team {
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
        return Team(
            id = teamUrl,
            name = teamName,
            abbreviation = abbreviation,
            country = teamCountry,
            bike = bike,
            website = website,
            jersey = jersey,
            year = year,
            riders = parseTeamRiders(teamDoc),
        )
    }

    private fun parseTeamRiders(teamDoc: Doc): List<TeamRider> =
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
                            TeamRider(
                                riderUrl,
                                riderName,
                            )
                        }
                    }

                }
            }
        }

    fun parseRiderDoc(riderDoc: Doc, riderName: String): Rider {
        val riderUrl = riderDoc.getDocUrl()
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
        val website = riderDoc.getElementWebsite()
        val unparsedDate = infoContent.ownText.split(' ').dropLast(1)
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
        val birthDate = LocalDate.parse(unparsedDate.joinToString(" "), formatter)
        val birthPlaceWeightAndHeight = infoContent.children.last().findFirst { text }.split(' ')
        val birthPlaceWordIndex = birthPlaceWeightAndHeight.indexOfFirst { it.lowercase().startsWith("birth") }
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
        return Rider(
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

    private fun getFirstAndLastName(fullName: String): Pair<String, String> {
        val index = max(fullName.indexOfFirst { it.isLowerCase() } - 2, fullName.indexOfFirst { it.isWhitespace() })
        val firstName = fullName.substring(index + 1, fullName.length)
        val lastName = fullName.substring(0, index).split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() }.trim() }
        return firstName to lastName
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