package unofficial

import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.*

val BANNED_TEAMS = setOf(TeamId(2179631))

object Unofficial {
  data class Result(
      val championshipMatches: List<Match>,
      val teamLeaderboard: List<TeamLeaderboard.Item>,
      val regionLeaderboard: List<RegionLeaderboard.Item>,
      val teamLogos: Map<TeamId, ByteArray>
  ) {
    fun toRawData(): RawData =
        RawData(championshipMatches, teamLeaderboard, regionLeaderboard)
  }

  fun orderMatches(matches: Collection<Match>): List<Match> =
      matches.toList().sortedBy { it.time.toEpochMilli() }

  /**
   * Given a set of matches, try to remove matches that belong to a single series (determined heuristically).
   *
   * <p>For each series, only the last (deciding) match will be outputted. If a series ends with a tie (say, a Bo2),
   * that series will be ignored altogether.
   *
   * <p>This function is idempotent.
   */
  fun coalesceSeries(matches: Set<Match>): Set<Match> {
    // For each (team, team, league) triple, we store the last match they've had.
    // If there is a new match for the same triple, we check whether it happened within a predefined period after
    // the previous one. If it did, it means it is the same "series" and we discard the previous one.

    // The triple type mentioned above. The assertion in the body is necessary to maintain equality semantics.
    data class Key(val team1: TeamId, val team2: TeamId, val league: LeagueId) {
      init {
        assert(team1.id < team2.id)
      }
    }

    fun keyFrom(match: Match): Key =
        if (match.winner.id < match.loser.id)
          Key(match.winner, match.loser, match.league)
        else
          Key(match.loser, match.winner, match.league)

    // Represents a currently ongoing series for each triple
    data class Series(val key: Key, val matches: List<Match>) {
      fun isTied(): Boolean =
          matches.count { it.winner == key.team1 } == matches.count { it.winner == key.team2 }
    }

    val THRESHOLD = Duration.ofHours(2)
    val seriesMap = HashMap<Key, Series>()
    val resultSeries = ArrayList<Series>()

    for (match in orderMatches(matches)) {
      val matchKey = keyFrom(match)

      if (!seriesMap.containsKey(matchKey)) {
        seriesMap.put(matchKey, Series(matchKey, listOf(match)))
      } else {
        val series = seriesMap[matchKey]!!

        // Time difference between the end of the last match and the beginning of the current one.
        val diff = Duration.between(series.matches.last().time, match.time.minus(match.duration))
        if (diff.seconds < THRESHOLD.seconds) {
          seriesMap.put(matchKey, Series(matchKey, series.matches.plus(match)))
        } else {
          if (!series.isTied()) {
            resultSeries.add(series)
          }

          seriesMap.put(matchKey, Series(matchKey, listOf(match)))
        }
      }
    }

    resultSeries.addAll(seriesMap.values)
    return resultSeries.map { it.matches.last() }.toSet()
  }

  private sealed class GetChampionshipMatchesResult {
    class Success(val matches: List<Match>) : GetChampionshipMatchesResult() {}
    class UnknownTeam(val team: TeamId, val matchId: MatchId) : GetChampionshipMatchesResult() {}
  }

  /**
   * Given a set of matches and an initial match (the first championship match), try to find a sequence of unofficial
   * championship matches.
   *
   * <p>This function actually does slightly more, as it not only outputs matches, but also keeps track of which
   * teams had their match history fetched.
   */
  private fun getChampionshipMatches(matches: Set<Match>, initialMatch: Match, knownTeams: Set<TeamId>)
      : GetChampionshipMatchesResult {

    val result = ArrayList<Match>()
    var currentChampion = initialMatch.winner
    for (match in orderMatches(coalesceSeries(matches))) {
      if (match.id.id <= initialMatch.id.id) {
        continue
      }

      if (match.loser == currentChampion) {
        if (!knownTeams.contains(match.winner)) {
          return GetChampionshipMatchesResult.UnknownTeam(match.winner, match.id)
        }

        result.add(match)
        currentChampion = match.winner
      }
    }

    return GetChampionshipMatchesResult.Success(result)
  }

  /**
   * Given a list of previous championship matches, try to extend it to the present moment.
   *
   * <p>It will fetch new match data from Dotabuff, coalesce series and output the input plus possibly any new matches.
   *
   * <p>So the algorithm here is rather tricky. Thanks to {@link DotabuffClient#getMatchesUntil} we can get all recent
   * matches of a team. Given a set of known matches, we can establish who is the most recent champion. Once we do that,
   * we get recent matches of this team, and add them to the set of known matches. It is possible that this
   * team has recently lost, and therefore the last champion of this updated set is now different.
   * We repeat the process until no unknown teams are encountered.
   */
  fun continueChampionshipMatches(previousMatches: List<Match>, dotabuff: DotabuffClient): List<Match> {
    val lastMatch = previousMatches.last()
    val knownMatches = hashSetOf(lastMatch)
    knownMatches.addAll(dotabuff.getMatchesUntil(lastMatch.winner, lastMatch.id))
    knownMatches.removeAll { BANNED_TEAMS.contains(it.winner) || BANNED_TEAMS.contains(it.loser) }
    val knownTeams = hashSetOf(lastMatch.winner)
    while (true) {
      val result = getChampionshipMatches(knownMatches, lastMatch, knownTeams)
      when (result) {
        is GetChampionshipMatchesResult.Success -> {
          return orderMatches(previousMatches.toSet().plus(result.matches.toSet()))
        }
        is GetChampionshipMatchesResult.UnknownTeam -> {
          knownMatches.addAll(dotabuff.getMatchesUntil(result.team, result.matchId))
          knownMatches.removeAll { BANNED_TEAMS.contains(it.winner) || BANNED_TEAMS.contains(it.loser) }
          knownTeams.add(result.team)
        }
      }
    }
  }

  fun process(
      previousChampionshipMatches: List<Match>,
      previousTeamLogos: Map<TeamId, ByteArray>,
      now: Instant,
      discardDuration: Duration = Duration.ofDays(1),
      webClient: WebClient
  ): Result {

    LOGGER.info("Last match: ${previousChampionshipMatches.last()}")

    val dotabuff = DotabuffClient(webClient)

    // Discard recent matches, otherwise corner cases can happen wrt. series coalescing.
    val actualPreviousMatches = previousChampionshipMatches.filter { it.time.isBefore(now.minus(discardDuration)) }
    check(actualPreviousMatches.isNotEmpty())

    val newMatches = continueChampionshipMatches(actualPreviousMatches, dotabuff)

    val teamLogos = HashMap(previousTeamLogos)
    for (match in newMatches) {
      if (!teamLogos.containsKey(match.winner)) {
        try {
          val logo = webClient.get(dotabuff.getTeamLogoUrl(match.winner).toString())
          teamLogos[match.winner] = logo
        } catch (ex: Exception) {
          LOGGER.error("Error while downloading logo for ${match.winner} (${match.winnerName})", ex)
        }
      }
    }

    val teamLeaderboard = TeamLeaderboard.compute(now, newMatches)
    val regionLeaderboard = RegionLeaderboard.compute(now, newMatches)

    return Result(newMatches, teamLeaderboard, regionLeaderboard, teamLogos)
  }
}