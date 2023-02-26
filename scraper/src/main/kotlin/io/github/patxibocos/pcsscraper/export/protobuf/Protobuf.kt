package io.github.patxibocos.pcsscraper.export.protobuf

import com.google.protobuf.Timestamp
import io.github.patxibocos.pcsscraper.entity.Race
import io.github.patxibocos.pcsscraper.entity.Rider
import io.github.patxibocos.pcsscraper.entity.Team
import io.github.patxibocos.pcsscraper.protobuf.CyclingDataOuterClass
import io.github.patxibocos.pcsscraper.protobuf.RaceOuterClass
import io.github.patxibocos.pcsscraper.protobuf.RiderOuterClass
import io.github.patxibocos.pcsscraper.protobuf.TeamOuterClass
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
        CyclingDataOuterClass.CyclingData.newBuilder().apply {
            addAllTeams(teamsProtobufMessage)
            addAllRiders(ridersProtobufMessage)
            addAllRaces(racesProtobufMessage)
        }.build()
    }

private fun buildTeamsProtobufMessage(teams: List<Team>): List<TeamOuterClass.Team> =
    teams.map { team ->
        TeamOuterClass.Team.newBuilder().apply {
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
            addAllRiderIds(team.riders)
        }.build()
    }

private fun buildRidersProtobufMessage(riders: List<Rider>): List<RiderOuterClass.Rider> =
    riders.map { rider ->
        RiderOuterClass.Rider.newBuilder().apply {
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
            rider.uciRankingPosition?.let { uciRankingPosition = it }
        }.build()
    }

private fun buildRacesProtobufMessage(races: List<Race>): List<RaceOuterClass.Race> =
    races.map { race ->
        RaceOuterClass.Race.newBuilder().apply {
            id = race.id
            name = race.name
            country = race.country
            race.website?.let { website = it }
            addAllStages(
                race.stages.map { stage ->
                    RaceOuterClass.Stage.newBuilder().apply {
                        id = stage.id
                        startDateTime = Timestamp.newBuilder()
                            .setSeconds(stage.startDateTime.epochSecond)
                            .build()
                        distance = stage.distance
                        profileType = when (stage.profileType) {
                            Race.Stage.ProfileType.FLAT -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_FLAT
                            Race.Stage.ProfileType.HILLS_FLAT_FINISH -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_HILLS_FLAT_FINISH
                            Race.Stage.ProfileType.HILLS_UPHILL_FINISH -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_HILLS_UPHILL_FINISH
                            Race.Stage.ProfileType.MOUNTAINS_FLAT_FINISH -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_MOUNTAINS_FLAT_FINISH
                            Race.Stage.ProfileType.MOUNTAINS_UPHILL_FINISH -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_MOUNTAINS_UPHILL_FINISH
                            null -> RaceOuterClass.Stage.ProfileType.PROFILE_TYPE_UNSPECIFIED
                        }
                        stage.departure?.let { departure = it }
                        stage.arrival?.let { arrival = it }
                        stageType = when (stage.stageType) {
                            Race.Stage.StageType.REGULAR -> RaceOuterClass.Stage.StageType.STAGE_TYPE_REGULAR
                            Race.Stage.StageType.INDIVIDUAL_TIME_TRIAL -> RaceOuterClass.Stage.StageType.STAGE_TYPE_INDIVIDUAL_TIME_TRIAL
                            Race.Stage.StageType.TEAM_TIME_TRIAL -> RaceOuterClass.Stage.StageType.STAGE_TYPE_TEAM_TIME_TRIAL
                        }
                        addAllResult(
                            stage.result.map { participantResult ->
                                RaceOuterClass.ParticipantResult.newBuilder().apply {
                                    position = participantResult.position
                                    participantId = participantResult.participant
                                    time = participantResult.time
                                }.build()
                            }
                        )
                        addAllGcResult(
                            stage.gcResult.map { participantResult ->
                                RaceOuterClass.ParticipantResult.newBuilder().apply {
                                    position = participantResult.position
                                    participantId = participantResult.participant
                                    time = participantResult.time
                                }.build()
                            }
                        )
                    }.build()
                }
            )
            addAllTeams(
                race.startList.map { teamParticipation ->
                    RaceOuterClass.TeamParticipation.newBuilder().apply {
                        teamId = teamParticipation.team
                        addAllRiders(
                            teamParticipation.riders.map { riderParticipation ->
                                RaceOuterClass.RiderParticipation.newBuilder().apply {
                                    riderId = riderParticipation.rider
                                    riderParticipation.number?.let { number = it }
                                }.build()
                            }
                        )
                    }.build()
                }
            )
            addAllResult(
                race.result.map { participantResult ->
                    RaceOuterClass.ParticipantResult.newBuilder().apply {
                        position = participantResult.position
                        participantId = participantResult.participant
                        time = participantResult.time
                    }.build()
                }
            )
        }.build()
    }