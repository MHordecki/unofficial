package unofficial

import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.*

object TeamLeaderboard {
  data class Item(val lastMatch: Match, val duration: Duration)

  fun compute(now: Instant, matches: List<Match>): List<Item> {
    data class Partial(val lastMatch: Match, val duration: Duration)

    val partials = HashMap<TeamId, Partial>()

    fun update(match: Match, now: Instant) {
      if (partials.containsKey(match.winner)) {
        partials[match.winner] = Partial(match, partials[match.winner]!!.duration.plus(Duration.between(match.time, now)))
      } else {
        partials[match.winner] = Partial(match, Duration.between(match.time, now))
      }
    }

    for ((prevMatch, match) in matches.zip(matches.drop(1))) {
      update(prevMatch, match.time)
    }

    update(matches.last(), now)

    return partials
        .toList()
        .map { Item(it.second.lastMatch, it.second.duration) }
        .sortedByDescending { it.duration.seconds }
  }
}

object RegionLeaderboard {
  val REGION_MAP = mapOf(
      TeamId(39).to(RegionId.AMERICA), // EG

      TeamId(15).to(RegionId.CHINA), // LGD
      TeamId(726228).to(RegionId.CHINA), // VG
      TeamId(2777247).to(RegionId.CHINA), // VG.R
      TeamId(1375614).to(RegionId.CHINA), // Newbee
      TeamId(2635099).to(RegionId.CHINA), // CDEC.Y

      TeamId(111474).to(RegionId.EUROPE), // Alliance
      TeamId(55).to(RegionId.EUROPE), // PR
      TeamId(2621843).to(RegionId.EUROPE), // Spirit
      TeamId(36).to(RegionId.EUROPE), // Na`vi
      TeamId(2586976).to(RegionId.EUROPE), // OG
      TeamId(2783913).to(RegionId.EUROPE), // No Diggity
      TeamId(2538753).to(RegionId.EUROPE), // Fantastic Five
      TeamId(46).to(RegionId.EUROPE), // Team Empire
      TeamId(59).to(RegionId.EUROPE), // Kaipi
      TeamId(2762745).to(RegionId.EUROPE), // Broodmothers
      TeamId(2006913).to(RegionId.EUROPE), // Vega
      TeamId(2163).to(RegionId.EUROPE), // Liquid
      TeamId(1838315).to(RegionId.EUROPE), // Secret
      TeamId(1883502).to(RegionId.EUROPE), // VP
      TeamId(2276247).to(RegionId.EUROPE), // Mamas Boys


      TeamId(1148284).to(RegionId.SEA), // MVP.Phoenix
      TeamId(2108395).to(RegionId.SEA), // TNC
      TeamId(24).to(RegionId.SEA) // Signature
  )

  data class Item(val region: RegionId, val lastMatch: Match, val duration: Duration)

  fun compute(now: Instant, matches: List<Match>): List<Item> =
      TeamLeaderboard.compute(now, matches)
          .map { Item(REGION_MAP[it.lastMatch.winner] ?: RegionId.ANTARCTICA, it.lastMatch, it.duration) }
          .groupBy { it.region }
          .map {
            Item(
                it.key,
                it.value.maxBy { it.lastMatch.id.id }!!.lastMatch,
                it.value.fold(Duration.ZERO, { d, r -> d.plus(r.duration) })
            )
          }
          .sortedByDescending { it.duration.seconds }
}

