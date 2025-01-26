package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.Cache
import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.export.json.json
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Duration.Companion.seconds

class PCSScraperTest : BehaviorSpec({

    given("a PCSScraper") {
        val cacheKeySlot = slot<String>()
        val cache = mockk<Cache> {
            every {
                get(key = capture(cacheKeySlot))
            } answers {
                this.javaClass.getResource("/pcssnapshot/html/${cacheKeySlot.captured}")!!.readText()
            }
        }
        val docFetcher = DocFetcher(cache, false, 1.seconds)
        val racesScraper = PCSRacesScraper(docFetcher)
        val ridersScraper = PCSRidersScraper(docFetcher)
        val teamsScraper = PCSTeamsScraper(docFetcher)

        var scrapedTeams: List<Team> = emptyList()
        var scrapedRiders: List<Rider> = emptyList()

        `when`("scrapping teams") {
            scrapedTeams = (teamsScraper.scrapeTeams(2025))
            val serializedJson = json.encodeToString(scrapedTeams)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/teams.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }

        `when`("scrapping riders") {
            scrapedRiders = (ridersScraper.scrapeRiders(2025, emptyList()))
            val serializedJson = json.encodeToString(scrapedRiders)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/riders.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }

        `when`("scrapping races") {
            val scrapedRaces =
                (racesScraper.scrapeRaces(2025, teamIdMapper(scrapedTeams)))
            val serializedJson = json.encodeToString(scrapedRaces)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/races.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }
    }
}) {
    override fun testCaseOrder(): TestCaseOrder {
        return TestCaseOrder.Sequential
    }
}
