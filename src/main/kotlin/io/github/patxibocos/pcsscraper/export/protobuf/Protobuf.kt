package io.github.patxibocos.pcsscraper.export.protobuf

import com.google.protobuf.Timestamp
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.protobuf.race.RaceOuterClass
import io.github.patxibocos.pcsscraper.protobuf.race.RacesOuterClass
import io.github.patxibocos.pcsscraper.protobuf.race.race
import io.github.patxibocos.pcsscraper.protobuf.race.races
import io.github.patxibocos.pcsscraper.protobuf.race.riderParticipation
import io.github.patxibocos.pcsscraper.protobuf.race.riderResult
import io.github.patxibocos.pcsscraper.protobuf.race.stage
import io.github.patxibocos.pcsscraper.protobuf.race.teamParticipation
import io.github.patxibocos.pcsscraper.protobuf.rider.RidersOuterClass
import io.github.patxibocos.pcsscraper.protobuf.rider.rider
import io.github.patxibocos.pcsscraper.protobuf.rider.riders
import io.github.patxibocos.pcsscraper.protobuf.team.TeamOuterClass
import io.github.patxibocos.pcsscraper.protobuf.team.TeamsOuterClass
import io.github.patxibocos.pcsscraper.protobuf.team.team
import io.github.patxibocos.pcsscraper.protobuf.team.teams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.time.ZoneOffset

internal suspend fun buildProtobufMessages(
    teams: List<Team>,
    riders: List<Rider>,
    races: List<Race>
): Triple<TeamsOuterClass.Teams, RidersOuterClass.Riders, RacesOuterClass.Races> =
    withContext(Dispatchers.Default) {
        val teamsProtobufMessage = async { buildTeamsProtobufMessage(teams) }
        val ridersProtobufMessage = async { buildRidersProtobufMessage(riders) }
        val racesProtobufMessage = async { buildRacesProtobufMessage(races) }
        val protobufMessages = awaitAll(teamsProtobufMessage, ridersProtobufMessage, racesProtobufMessage)
        Triple(
            protobufMessages[0] as TeamsOuterClass.Teams,
            protobufMessages[1] as RidersOuterClass.Riders,
            protobufMessages[2] as RacesOuterClass.Races
        )
    }

private fun buildTeamsProtobufMessage(_teams: List<Team>): TeamsOuterClass.Teams =
    teams {
        teams.addAll(
            _teams.map { team ->
                team {
                    id = team.id
                    name = team.name
                    status = when (team.status) {
                        Team.Status.WT -> TeamOuterClass.Team.Status.STATUS_WORLD_TEAM
                        Team.Status.PRT -> TeamOuterClass.Team.Status.STATUS_PRO_TEAM
                    }
                    abbreviation = team.abbreviation
                    country = team.country
                    bike = team.bike
                    jersey = team.jersey.toString()
                    website = team.website.orEmpty()
                    year = team.year
                    riderIds.addAll(team.riders)
                }
            }
        )
    }

private fun buildRidersProtobufMessage(_riders: List<Rider>): RidersOuterClass.Riders =
    riders {
        riders.addAll(
            _riders.map { rider ->
                rider {
                    id = rider.id
                    firstName = rider.firstName
                    lastName = rider.lastName
                    country = rider.country
                    website = rider.website.orEmpty()
                    birthDate =
                        Timestamp.newBuilder()
                            .setSeconds(rider.birthDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                            .build()
                    birthPlace = rider.birthPlace.orEmpty()
                    weight = rider.weight ?: 0
                    height = rider.weight ?: 0
                    photo = rider.photo.toString()
                }
            }
        )
    }

private fun buildRacesProtobufMessage(_races: List<Race>): RacesOuterClass.Races =
    races {
        races.addAll(
            _races.map { race ->
                race {
                    id = race.id
                    name = race.name
                    country = race.country
                    startDate =
                        Timestamp.newBuilder()
                            .setSeconds(race.startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                            .build()
                    endDate =
                        Timestamp.newBuilder().setSeconds(race.endDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                            .build()
                    website = race.website.orEmpty()
                    stages.addAll(
                        race.stages.map { stage ->
                            stage {
                                id = stage.id
                                startDate = Timestamp.newBuilder()
                                    .setSeconds(stage.startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                                    .build()
                                distance = stage.distance
                                type = when (stage.type) {
                                    Race.Stage.Type.FLAT -> RaceOuterClass.Stage.Type.TYPE_FLAT
                                    Race.Stage.Type.HILLS_FLAT_FINISH -> RaceOuterClass.Stage.Type.TYPE_HILLS_FLAT_FINISH
                                    Race.Stage.Type.HILLS_UPHILL_FINISH -> RaceOuterClass.Stage.Type.TYPE_HILLS_UPHILL_FINISH
                                    Race.Stage.Type.MOUNTAINS_FLAT_FINISH -> RaceOuterClass.Stage.Type.TYPE_MOUNTAINS_FLAT_FINISH
                                    Race.Stage.Type.MOUNTAINS_UPHILL_FINISH -> RaceOuterClass.Stage.Type.TYPE_MOUNTAINS_UPHILL_FINISH
                                    null -> RaceOuterClass.Stage.Type.TYPE_UNSPECIFIED
                                }
                                departure = stage.departure.orEmpty()
                                arrival = stage.arrival.orEmpty()
                                result.addAll(
                                    stage.result.map { riderResult ->
                                        riderResult {
                                            position = riderResult.position
                                            riderId = riderResult.rider
                                            time = riderResult.time
                                        }
                                    }
                                )
                            }
                        }
                    )
                    teams.addAll(
                        race.startList.map { teamParticipation ->
                            teamParticipation {
                                teamId = teamParticipation.team
                                riders.addAll(
                                    teamParticipation.riders.map { riderParticipation ->
                                        riderParticipation {
                                            riderId = riderParticipation.rider
                                            number = riderParticipation.number ?: 0
                                        }
                                    }
                                )
                            }
                        }
                    )
                    result.addAll(
                        race.result.map { riderResult ->
                            riderResult {
                                position = riderResult.position
                                riderId = riderResult.rider
                                time = riderResult.time
                            }
                        }
                    )
                }
            }
        )
    }