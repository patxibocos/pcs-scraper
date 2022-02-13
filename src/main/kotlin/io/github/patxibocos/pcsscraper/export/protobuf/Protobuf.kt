package io.github.patxibocos.pcsscraper.export.protobuf

import com.google.protobuf.Timestamp
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.protobuf.CyclingDataOuterClass
import io.github.patxibocos.pcsscraper.protobuf.RaceOuterClass
import io.github.patxibocos.pcsscraper.protobuf.RiderOuterClass
import io.github.patxibocos.pcsscraper.protobuf.TeamOuterClass
import io.github.patxibocos.pcsscraper.protobuf.cyclingData
import io.github.patxibocos.pcsscraper.protobuf.race
import io.github.patxibocos.pcsscraper.protobuf.rider
import io.github.patxibocos.pcsscraper.protobuf.riderParticipation
import io.github.patxibocos.pcsscraper.protobuf.riderResult
import io.github.patxibocos.pcsscraper.protobuf.stage
import io.github.patxibocos.pcsscraper.protobuf.team
import io.github.patxibocos.pcsscraper.protobuf.teamParticipation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset

internal suspend fun buildCyclingDataProtobuf(
    teams: List<Team>,
    riders: List<Rider>,
    races: List<Race>
): CyclingDataOuterClass.CyclingData =
    withContext(Dispatchers.Default) {
        val teamsProtobufMessage = buildTeamsProtobufMessage(teams)
        val ridersProtobufMessage = buildRidersProtobufMessage(riders)
        val racesProtobufMessage = buildRacesProtobufMessage(races)
        cyclingData {
            this.teams.addAll(teamsProtobufMessage)
            this.riders.addAll(ridersProtobufMessage)
            this.races.addAll(racesProtobufMessage)
        }
    }

private fun buildTeamsProtobufMessage(_teams: List<Team>): List<TeamOuterClass.Team> =
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
            team.website?.let { website = it }
            year = team.year
            riderIds.addAll(team.riders)
        }
    }

private fun buildRidersProtobufMessage(_riders: List<Rider>): List<RiderOuterClass.Rider> =
    _riders.map { rider ->
        rider {
            id = rider.id
            firstName = rider.firstName
            lastName = rider.lastName
            country = rider.country
            rider.website?.let { website = it }
            rider.birthDate?.let {
                birthDate = Timestamp.newBuilder()
                    .setSeconds(it.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                    .build()
            }
            rider.birthPlace?.let { birthPlace = it }
            rider.weight?.let { weight = it }
            rider.height?.let { height = it }
            photo = rider.photo.toString()
        }
    }

private fun buildRacesProtobufMessage(_races: List<Race>): List<RaceOuterClass.Race> =
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
            race.website?.let { website = it }
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
                        stage.departure?.let { departure = it }
                        stage.arrival?.let { arrival = it }
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
                                    riderParticipation.number?.let { number = it }
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