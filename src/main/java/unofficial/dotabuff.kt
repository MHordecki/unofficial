package unofficial

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZonedDateTime
import java.net.URL
import java.util.*

data class ScrapedTeam(
    val id: TeamId,
    val name: String,
    val logoUrl: URL
) {}

class DotabuffClient(val webClient: WebClient) {
  fun getTeamLogoUrl(team: TeamId): URL {
    val url = "http://www.dotabuff.com/esports/teams/${team.id}"

    try {
      return parseTeamPage(webClient.getHtml(url), URL(url))
    } catch (ex: Exception) {
      throw IllegalStateException("Failed to process $url", ex)
    }
  }

  fun parseTeamPage(html: String, baseUrl: URL): URL {
    val doc = Jsoup.parse(html)
    return URL(baseUrl, doc.select("div.header-content img.img-team").first().attr("src"))
  }

  fun getMatch(match: MatchId): Match {
    val url = "http://www.dotabuff.com/matches/${match.id}"

    try {
      return parseMatchPage(webClient.getHtml(url), URL(url))
    } catch (ex: Exception) {
      throw IllegalStateException("Failed to process $url", ex)
    }
  }

  private fun parseMatchPage(html: String, url: URL): Match {
    val doc = Jsoup.parse(html)
    val titleRegex = Regex("""Match (\d+) .*""")
    val matchId = MatchId(titleRegex.matchEntire(doc.title())!!.groupValues[1].toLong())

    val details = doc.select("div.header-content-secondary").single().getElementsByTag("dd")

    val leagueLink = details[0].getElementsByTag("a").single()
    val leagueRegex = Regex("""/esports/leagues/(\d+)""")
    val leagueId = LeagueId(leagueRegex.matchEntire(leagueLink.attr("href"))!!.groupValues[1].toLong())
    val leagueName = leagueLink.text()

    val duration = parseDuration(details[3].getElementsByTag("dd").single().text())
    val time = Instant.from(ZonedDateTime.parse(details[4].getElementsByTag("time").single()
        .attr("datetime")))

    val resultDiv = doc.select("div.match-result").single()
    val radiantWon: Boolean
    when {
      resultDiv.hasClass("radiant") -> radiantWon = true
      resultDiv.hasClass("dire") -> radiantWon = false
      else -> throw IllegalStateException("Unrecognized match result: $resultDiv")
    }

    val radiant = parseTeamLogo(doc.select(".team-results section.radiant header img").first(), url)
    val dire = parseTeamLogo(doc.select(".team-results section.dire header img").first(), url)
    val winner = if (radiantWon) radiant else dire
    val loser = if (radiantWon) dire else radiant

    return Match(
        id = matchId,
        winner = winner.id,
        winnerName = winner.name,
        loser = loser.id,
        loserName = loser.name,
        league = leagueId,
        leagueName = leagueName,
        time = time,
        duration = duration)
  }

  fun getTeamMatches(team: TeamId, page: Int): List<Match> {
    val url = "http://www.dotabuff.com/esports/teams/${team.id}/matches?page=${page + 1}"

    try {
      return parseMatchList(webClient.getHtml(url), URL(url))
    } catch (ex: Exception) {
      throw IllegalStateException("Failed to process $url", ex)
    }
  }

  private fun parseMatchList(html: String, url: URL): List<Match> {
    val doc = Jsoup.parse(html)

    val thisTeam = parseTeamLogo(
        doc.getElementsByClass("header-content").single().getElementsByTag("img").single(),
        url)

    return doc
        .getElementsByTag("article")
        .single()
        .getElementsByClass("recent-esports-matches")
        .single()
        .getElementsByTag("tbody")
        .single()
        .getElementsByTag("tr")
        .mapNotNull { parseMatchRow(it, thisTeam, url) }
  }

  private fun parseTeamLogo(logoImg: Element, baseUrl: URL): ScrapedTeam {
    val teamIdRegex = Regex("""/esports/teams/(.*?)/tooltip""")
    val match = teamIdRegex.matchEntire(logoImg.attr("data-tooltip-url"))!!
    return ScrapedTeam(
        id = TeamId(match.groupValues[1].toLong()),
        name = logoImg.attr("alt"),
        logoUrl = URL(baseUrl, logoImg.attr("src")))
  }

  private fun parseDuration(duration: String): Duration {
    val durationRegex = Regex("""((\d+):)?(\d+):(\d+)""")
    val (x, h, m, s) = durationRegex.matchEntire(duration)!!.destructured
    x.isEmpty() // So that Kotlin shuts up about unused variables
    if (h.isNotEmpty()) {
      return Duration.ofHours(h.toLong()) + Duration.ofMinutes(m.toLong()) + Duration.ofSeconds(s.toLong())
    } else {
      return Duration.ofMinutes(m.toLong()) + Duration.ofSeconds(s.toLong())
    }
  }

  private fun parseMatchRow(row: Element, thisTeam: ScrapedTeam, baseUrl: URL): Match? {
    val cols = row.getElementsByTag("td")

    val matchLink = cols[0].getElementsByTag("a").single()
    val matchIdRegex = Regex("""/matches/(.*)""")
    val id = MatchId(matchIdRegex.matchEntire(matchLink.attr("href"))!!.groupValues[1].toLong())
    val won: Boolean
    when {
      matchLink.hasClass("won") -> won = true
      matchLink.hasClass("lost") -> won = false
      else -> throw IllegalArgumentException("Unrecognizable match result: $matchLink")
    }

    // Format: 2016-03-03T04:04:15+00:00
    val timeSpec = cols[0].getElementsByTag("time").single().attr("datetime")
    val shiftedTime = Instant.from(ZonedDateTime.parse(timeSpec))
    // Don't ask me why...
    val time = shiftedTime.plusSeconds(90)


    val leagueImg = cols[1].getElementsByTag("img").single()
    val leagueIdRegex = Regex("""/esports/leagues/(.*?)/tooltip""")
    val leagueId = LeagueId(leagueIdRegex.matchEntire(leagueImg.attr("data-tooltip-url"))!!.groupValues[1].toLong())
    val leagueName = leagueImg.attr("alt")
    if (leagueName == "Unknown") {
      return null
    }

    val duration = parseDuration(cols[2].text())

    val opponentImgs = cols[4].getElementsByClass("img-team")
    if (opponentImgs.size != 1) {
      // This happens sometimes when the opposing team is unknown to Dotabuff
      return null
    }

    val opponent = parseTeamLogo(opponentImgs.single(), baseUrl)
    val winner = if (won) thisTeam else opponent
    val loser = if (won) opponent else thisTeam

    return Match(
        id = id,
        league = leagueId,
        leagueName = leagueName,
        winner = winner.id,
        winnerName = winner.name,
        loser = loser.id,
        loserName = loser.name,
        duration = duration,
        time = time)
  }

  fun getMatchesUntil(team: TeamId, until: MatchId): List<Match> {
    val seen = ArrayList<Match>()
    var page = 0

    while (!seen.any { it.id == until }) {
      for (match in seen) {
        if (match.id.id < until.id) {
          throw IllegalStateException(
              "When fetching matches of $team, ${match.id} was encountered before $until")
        }
      }

      seen.addAll(getTeamMatches(team, page))
      page += 1
    }

    return seen
  }

}
