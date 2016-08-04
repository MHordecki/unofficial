package unofficial

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

interface Input {
  fun getPreviousMatches(): List<Match>
  fun getLogos(): Map<TeamId, ByteArray>
}

data class RawData(
    val championshipMatches: List<Match>,
    val teamLeaderboard: List<TeamLeaderboard.Item>,
    val regionLeaderboard: List<RegionLeaderboard.Item>)

class FileBasedInput(val io: Io, val mapper: ObjectMapper) : Input {
  override fun getPreviousMatches(): List<Match> {
    val index = io.read("index.txt").toString(StandardCharsets.UTF_8).trim()
    LOGGER.info("Index says: $index")

    return mapper.readValue(io.read("$index/data.json"), SavedData::class.java).matches
  }

  override fun getLogos(): Map<TeamId, ByteArray> {
    val index = io.read("index.txt").toString(StandardCharsets.UTF_8).trim()
    LOGGER.info("Index says: $index")

    return mapper
        .readValue(io.read("$index/data.json"), SavedData::class.java)
        .savedLogos
        .map { it.to(io.read("$index/team.${it.id}.png")) }
        .toMap()
  }
}

class SeedBasedInput(val dotabuff: DotabuffClient, val matchId: MatchId) : Input {
  override fun getPreviousMatches(): List<Match> =
      listOf(dotabuff.getMatch(matchId))

  override fun getLogos() = mapOf<TeamId, ByteArray>()
}

