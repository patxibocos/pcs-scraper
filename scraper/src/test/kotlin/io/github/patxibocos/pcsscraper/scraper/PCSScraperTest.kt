package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.document.Cache
import io.github.patxibocos.pcsscraper.document.DocFetcher
import io.github.patxibocos.pcsscraper.export.json.json
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.encodeToString
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

        `when`("scrapping teams") {
            val scrapedTeams = (teamsScraper.scrapeTeams(2023))
            val serializedJson = json.encodeToString(scrapedTeams)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/teams.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }

        `when`("scrapping riders") {
            val scrapedRaces = (ridersScraper.scrapeRiders(2023))
            val serializedJson = json.encodeToString(scrapedRaces)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/riders.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }

        `when`("scrapping races") {
            val scrapedRaces = (racesScraper.scrapeRaces(2023) { it })
            val serializedJson = json.encodeToString(scrapedRaces)
            then("it should be equal to the snapshot") {
                val expectedJson = this.javaClass.getResource("/pcssnapshot/json/races.json")!!.readText()
                serializedJson shouldBe expectedJson
            }
        }
    }
})
