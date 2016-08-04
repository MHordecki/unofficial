package unofficial

import com.google.cloud.AuthCredentials
import com.google.cloud.storage.StorageOptions
import org.threeten.bp.Instant
import java.io.File
import java.io.FileInputStream


object SampleRunnable {
  @JvmStatic
  fun main(args: Array<String>) {
    CachingWebClient(File("http_cache2")).use { webClient ->
      //
      val input = SeedBasedInput(DotabuffClient(webClient), MatchId(271145478 ))
//      val input = SeedBasedInput(DotabuffClient(webClient), MatchId(1841061365))
      val output = GcsIo(StorageOptions.builder()
          .projectId("undisputed-1256")
          .authCredentials(AuthCredentials.createForJson(FileInputStream("undisputed-399aa8bf63de.json")))
          .build().service(), "www.unofficialdota2champions.com")

      Pipeline.run(
          now = Instant.now(),
          input = input,
          webClient = webClient,
          urlBase = "",
          //          urlBase = "/undisputed/gen",
          output = output,
          includeAnalytics = true
          //          overrideDirectoryName = "gen"
      )
    }
  }
}