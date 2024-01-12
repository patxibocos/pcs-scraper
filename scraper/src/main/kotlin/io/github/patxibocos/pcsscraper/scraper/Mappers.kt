package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team

fun teamIdMapper(teams: List<Team>): (String) -> String? = { teamId: String ->
    teamId.takeIf { teams.map { team -> team.id }.contains(it) }
}

fun riderIdMapper(riders: List<Rider>): (String) -> String? = { riderId: String ->
    riderId.takeIf { riders.map { rider -> rider.id }.contains(it) }
}
