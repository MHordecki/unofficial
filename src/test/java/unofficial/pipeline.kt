package unofficial

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.tools.cloudstorage.GcsFilename
import com.google.appengine.tools.cloudstorage.GcsService
import com.google.appengine.tools.cloudstorage.GcsServiceFactory
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig
import com.google.appengine.tools.development.testing.LocalServiceTestHelper
import com.google.inject.Guice
import com.google.protobuf.TextFormat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.threeten.bp.Instant
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets


@RunWith(JUnit4::class)
class PipelineTest {
  /**
   * This test runs the complete pipeline on historical data.
   *
   * It begins with 2278508703: MVP.Phoenix beats Can't Say Wips
   */
  @Test
  fun testPipeline() {
    val webClient = TestResourceWebClient(javaClass)
    val io = InMemoryIo()
    val input = FileBasedInput(io, Json.getObjectMapper())

    io.write("index.txt", "1970-01-01T00:00:00Z".toByteArray())
    io.write("1970-01-01T00:00:00Z/data.json", webClient.get("pipeline_test/input_changes.json"))

    Pipeline.run(
        now = Instant.parse("1971-01-01T00:00:00Z"),
        input = input,
        webClient = webClient,
        urlBase = "",
        output = io
        )

    Assert.assertEquals("1971-01-01T00:00:00Z",
        io.read("index.txt").toString(StandardCharsets.UTF_8))

    val mapper = Json.getObjectMapper()
    val expectedData = mapper.readValue(webClient.get("pipeline_test/expected_raw.json"),
        RawData::class.java)
    val resultData = mapper.readValue(io.read("1971-01-01T00:00:00Z/raw.json"),
        RawData::class.java)
    Assert.assertArrayEquals(expectedData.championshipMatches.toTypedArray(),
        resultData.championshipMatches.toTypedArray())

    Assert.assertEquals(io.read("index.html").toString(StandardCharsets.UTF_8),
        io.read("1971-01-01T00:00:00Z/index.html").toString(StandardCharsets.UTF_8))
  }
}