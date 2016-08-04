package unofficial

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.common.collect.Ordering
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.*

class SerializerModule : SimpleModule("SerializerModule") {
  init {
    add(MatchId::class.java,
        { id, gen -> gen.writeNumber(id.id) },
        { MatchId(it.longValue) })

    add(TeamId::class.java,
        { id, gen -> gen.writeNumber(id.id) },
        { TeamId(it.longValue) })

    add(LeagueId::class.java,
        { id, gen -> gen.writeNumber(id.id) },
        { LeagueId(it.longValue) })

    add(Duration::class.java,
        { dur, gen -> gen.writeNumber(dur.seconds) },
        { Duration.ofSeconds(it.longValue) })

    add(Instant::class.java,
        { ins, gen -> gen.writeString(ins.toString()) },
        { Instant.parse(it.valueAsString) })
  }

  fun<T> add(cls: Class<T>, serializer: (T, JsonGenerator) -> Unit,
      deserializer: (JsonParser) -> T) {
    addSerializer(cls, object : JsonSerializer<T>() {
      override fun serialize(value: T, gen: JsonGenerator?, prov: SerializerProvider?) {
        serializer(value!!, gen!!)
      }
    })

    addDeserializer(cls, object : JsonDeserializer<T>() {
      override fun deserialize(parser: JsonParser?, ctx: DeserializationContext?): T {
        return deserializer(parser!!)
      }
    })
  }
}

data class MatchId(val id: Long) {
  override fun toString() = "Match:$id"
}

data class TeamId(val id: Long) {
  override fun toString() = "Team:$id"
}

data class LeagueId(val id: Long) {
  override fun toString() = "League:$id"
}

enum class RegionId {
  EUROPE,
  AMERICA,
  SEA,
  CHINA,
  ANTARCTICA
}

data class Match(
    val id: MatchId,
    val league: LeagueId,
    val leagueName: String,
    val winner: TeamId,
    val winnerName: String,
    val loser: TeamId,
    val loserName: String,
    val duration: Duration,
    val time: Instant) {
  override fun equals(other: Any?) = other is Match && other.id == id
  override fun hashCode() = id.hashCode()
}

data class SavedData(val matches: List<Match>, val savedLogos: List<TeamId>) {
}
