package unofficial

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.threeten.bp.*
import java.net.URL

@RunWith(JUnit4::class)
class DotabuffTest {
  val resources = TestResourceWebClient(javaClass)
  val expectedMatch = Match(
      id = MatchId(2193255935L),
      league = LeagueId(4266),
      leagueName = "The Shanghai Major",
      winner = TeamId(1838315),
      winnerName = "Team Secret",
      loser = TeamId(2586976),
      loserName = "OG Dota2",
      duration = Duration.ofSeconds(35 * 60 + 46),
      time = Instant.from(ZonedDateTime.of(LocalDateTime.of(2016, 3, 3, 4, 5, 45), ZoneId.of("UTC"))))

  @Test
  fun testGetMatch() {
    val result = DotabuffClient(resources).getMatch(MatchId(2193255935L))

    // This match inexplicably has as different ending time than when viewed via a match list!
    val expected = expectedMatch.copy(
        time = Instant.from(ZonedDateTime.of(LocalDateTime.of(2016, 3, 3, 4, 5, 45), ZoneId.of("UTC"))))
    Assert.assertEquals(expected, result)
  }

  @Test
  fun testGetTeamMatches() {
    // This test uses a saved match list of Team Secret
    val result = DotabuffClient(resources).getTeamMatches(TeamId(1838315), page = 1)

    Assert.assertEquals(20, result.size)
    Assert.assertEquals(expectedMatch, result[0])
  }

  @Test
  fun testGetTeamLogoUrl() {
    val result = DotabuffClient(resources).getTeamLogoUrl(TeamId(111474))

    Assert.assertEquals(URL("http://riki.dotabuff.net/t/l/Sy9XpIvI58.png"), result)
  }
}
