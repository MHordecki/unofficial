package unofficial

import com.google.common.io.Resources
import org.threeten.bp.Instant


//data class Destination(val path: String) {}
//data class Now(val now: Instant) {}

//class PipelineModule : AbstractModule() {
//  class NowFlag : StringFlag("now", metavar = "TIME", help = "Current time as seen by the pipeline (ISO-8601)")
//
//  override fun configure() {
//    Flags.installFromModule(binder(), this)
//
//    bind(ObjectMapper::class.java)
//        .toInstance(ObjectMapper().registerModules(KotlinModule(), SerializerModule()))
//  }
//
//  @Provides
//  @Singleton
//  fun provideNow(now: NowFlag): Now =
//      if (now.value.isNotBlank())
//        Now(Instant.parse(now.value))
//      else
//        Now(Instant.now())
//}
//
//
//data class CoalescingKey(val team1: TeamId, val team2: TeamId, val league: LeagueId) {
//  companion object {
//    fun from(match: Match): CoalescingKey {
//      if (match.winner.id > match.loser.id) {
//        return CoalescingKey(match.winner, match.loser, match.league)
//      } else {
//        return CoalescingKey(match.loser, match.winner, match.league)
//      }
//    }
//  }
//}
//
//fun getMatchScorePair(match: Match): Pair<Int, Int> {
//  val key = CoalescingKey.from(match)
//  if (key.team1 == match.winner) {
//    return Pair(1, 0)
//  } else {
//    return Pair(0, 1)
//  }
//}
//
//data class CoalescingItem(
//    val key: CoalescingKey,
//    val score: Pair<Int, Int>, // Used to determine, and remove, ties
//    val lastMatch: Match) {
//  fun add(match: Match): CoalescingItem {
//    check(CoalescingKey.from(match) == key)
//    return copy(score = score.pairwisePlus(getMatchScorePair(match)), lastMatch = match)
//  }
//}
//
//fun coalesceMatches(matches: List<Match>): List<Match> {
//  val items = HashMap<CoalescingKey, CoalescingItem>()
//  val result = ArrayList<Match>()
//  val threshold = Duration.ofHours(2)
//
//  for (match in cleanUpMatches(matches)) {
//    val key = CoalescingKey.from(match)
//
//    if (items.containsKey(key)) {
//      val item = items[key]!!
//      val diff = Duration.between(item.lastMatch.time, match.time.minus(match.duration))
//      if (diff.seconds >= threshold.seconds) {
//        if (item.score.first != item.score.second) {
//          result.add(item.lastMatch)
//        }
//
//        items.put(key, CoalescingItem(key, getMatchScorePair(match), match))
//      } else {
//        items.put(key, item.add(match))
//      }
//    } else {
//      items.put(key, CoalescingItem(key, getMatchScorePair(match), match))
//    }
//  }
//
//  for (item in items.values) {
//    if (item.score.first != item.score.second) {
//      result.add(item.lastMatch)
//    }
//  }
//
//  return result
//}
//
//sealed class ComputeChangesResult {
//  class Success(val changes: List<Match>) : ComputeChangesResult() {}
//  class UnknownTeam(val team: TeamId, val matchId: MatchId) : ComputeChangesResult() {}
//}
//
//fun computeChanges(matches: List<Match>, previousChange: Match, knownTeams: Set<TeamId>): ComputeChangesResult {
//  val result = ArrayList<Match>()
//  var currentChampion = previousChange.winner
//  for (match in cleanUpMatches(coalesceMatches(matches))) {
//    if (match.id.id <= previousChange.id.id) {
//      continue
//    }
//
//    if (match.loser == currentChampion) {
//      if (!knownTeams.contains(match.winner)) {
//        return ComputeChangesResult.UnknownTeam(match.winner, match.id)
//      }
//
//      result.add(match)
//      currentChampion = match.winner
//    }
//  }
//
//  return ComputeChangesResult.Success(result)
//}
//
//fun cleanUpMatches(matches: List<Match>): List<Match> =
//    matches.sortedBy { it.id.id }.distinctBy { it.id }

object Pipeline {
  fun run(
      now: Instant,
      input: Input,
      output: Io,
      urlBase: String,
      webClient: WebClient,
      overrideDirectoryName: String? = null,
      includeAnalytics: Boolean = false
  ) {
    LOGGER.info("Starting pipeline: ${now}")

    val previousMatches = input.getPreviousMatches()
    LOGGER.info("Got ${previousMatches.size} previous matches")

    val teamLogos = input.getLogos()
    LOGGER.info("Got ${teamLogos.size} previous logos")

    val result = Unofficial.process(
        previousChampionshipMatches = previousMatches,
        previousTeamLogos = teamLogos,
        now = now,
        webClient = webClient)

    val directory = overrideDirectoryName ?: "$now"
    val runUrlBase = "$urlBase/$directory"

    val page = HtmlGenerator.generate(
        now = now,
        urlBase = runUrlBase,
        result = result,
        includeAnalytics = includeAnalytics)

    val mapper = Json.getObjectMapper()

    output.write("$directory/index.html", page.toByteArray())
    output.write("$directory/raw.json", mapper.writeValueAsBytes(result.toRawData()))
    for ((team, logo) in result.teamLogos) {
      output.write("$directory/team.${team.id}.png", logo)
    }

    output.write("index.txt", directory.toByteArray())
    output.write("index.html", page.toByteArray())
    output.write("raw.json", mapper.writeValueAsBytes(result.toRawData()))

    fun copyFile(name: String) {
      output.write("$directory/$name", Resources.toByteArray(javaClass.getResource(name)))
    }
    copyFile("style.css")
    copyFile("js.js")
    copyFile("GitHub-Mark-32px.png")
  }
}
