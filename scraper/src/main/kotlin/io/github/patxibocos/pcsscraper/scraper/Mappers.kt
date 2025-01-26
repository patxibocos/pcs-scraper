package io.github.patxibocos.pcsscraper.scraper

import io.github.patxibocos.pcsscraper.entity.Team

fun teamIdMapper(teams: List<Team>): (String) -> String? = { teamId: String ->
    teamId.takeIf { teams.map { team -> team.id }.contains(it) }
}
