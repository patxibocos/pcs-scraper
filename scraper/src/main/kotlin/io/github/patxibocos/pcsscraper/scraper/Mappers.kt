package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team

fun teamIdMapper(teams: List<Team>) = { teamId: String ->
    when {
        teams.map { it.id }.contains(teamId) -> teamId
        teamId == "team-dsm-2023" -> "team-dsm-firmenich-2023"
        teamId == "trek-segafredo-2023" -> "lidl-trek-2023"
        teamId == "team-corratec-2023" -> "team-corratec-selle-italia-2023"
        teamId == "unisa-australia-2023" -> null
        teamId == "switzerland-2023" -> null
        teamId == "poland-2023" -> null
        else -> throw IllegalArgumentException("Unexpected team id: $teamId")
    }
}

fun riderIdMapper(riders: List<Rider>) = { riderId: String ->
    riderId.takeIf { riders.map { rider -> rider.id }.contains(it) }
}
