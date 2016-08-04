/**
 * This file contains HTML generating routines.
 *
 * It uses Freemarker as its templating engine. Boy, does it suck. Sadly, all other engines for the JVM suck even more.
 */
package unofficial

import freemarker.ext.beans.StringModel
import freemarker.template.Configuration
import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.StringWriter
import java.util.*

// An override map for needlessly long league names
val LEAGUE_NAME_OVERRIDES = mapOf(
    "Captains Draft 3.0 Presented by DotaCinema & MoonduckTV".to("Captains Draft 3.0")
)



// Used to pass arguments to the template in a typesafe way.
data class TemplateContext(
    // Prefix to put before each URL. This is needed because files for each run are put in a subdirectory,
    // but the HTTP server is using index.html from the root to serve.
    val urlBase: String,

    // Run ID displayed in the footer.
    val now: Instant,

    // Self-explanatory
    val history: List<Match>,
    val teamLeaderboard: List<TeamLeaderboard.Item>,
    val regionLeaderboard: List<RegionLeaderboard.Item>,

    // Champion is the winner of this match
    val champion: Match,

    val includeAnalytics: Boolean
) {
  private fun templateMethod(fn: (Any) -> Any): TemplateMethodModelEx =
      object : TemplateMethodModelEx {
        override fun exec(args: MutableList<Any?>?): Any? {
          val arg = args!![0]!!
          // I have no idea what the conceptual model behind Freemarker is and frankly I don't care
          return when (arg) {
            is StringModel -> fn(arg.wrappedObject)
            is SimpleScalar -> fn(arg.asString)
            else -> fn(arg)
          }
        }
      }

  val unbreakable = templateMethod { (it as String).replace(" ", "&nbsp;") }

  val getMatchUrl = templateMethod { "http://www.dotabuff.com/matches/${(it as MatchId).id}" }
  val getLeagueUrl = templateMethod { "http://www.dotabuff.com/esports/leagues/${(it as LeagueId).id}" }
  val getTeamUrl = templateMethod { "http://www.dotabuff.com/esports/teams/${(it as TeamId).id}" }

  val displayLeagueName = templateMethod { LEAGUE_NAME_OVERRIDES[it as String] ?: it }
  val displayDuration = templateMethod {
    it as Duration
    listOf(
        if (it.toDays() != 0L) "${it.toDays()}d" else null,
        if (it.toHours() % 24 != 0L) "${it.toHours() % 24}h" else null)
        .filterNotNull()
        .joinToString(" ")
  }

  val displayDate = templateMethod {
    DateTimeFormatter.ofPattern("MMM d, yyyy").format(ZonedDateTime.ofInstant(it as Instant, ZoneId.of("UTC")))
  }
  val displayShortDate = templateMethod {
    DateTimeFormatter.ofPattern("MMM yyyy").format(ZonedDateTime.ofInstant(it as Instant, ZoneId.of("UTC")))
  }

  val displayRegion = templateMethod {
    when (it as RegionId) {
      RegionId.EUROPE -> "Europe"
      RegionId.AMERICA -> "America"
      RegionId.SEA -> "SEA"
      RegionId.ANTARCTICA -> "Antarctica"
      RegionId.CHINA -> "China"
    }
  }
}


object HtmlGenerator {
  fun generate(
      now: Instant,
      urlBase: String,
      result: Unofficial.Result,
      includeAnalytics: Boolean = false
  ): String {
    val context = TemplateContext(
        urlBase = "$urlBase",

        now = now,

        history = result.championshipMatches,
        teamLeaderboard = result.teamLeaderboard,
        regionLeaderboard = result.regionLeaderboard,

        champion = result.championshipMatches.last(),

        includeAnalytics = includeAnalytics
    )

    val cfg = Configuration(Configuration.VERSION_2_3_24)
    cfg.setClassForTemplateLoading(javaClass, "")
    cfg.defaultEncoding = "UTF-8"
    cfg.templateExceptionHandler = freemarker.template.TemplateExceptionHandler.RETHROW_HANDLER
    cfg.logTemplateExceptions = true

    val writer = StringWriter()
    cfg.getTemplate("template.html").process(context, writer)
    return writer.toString()
  }
}