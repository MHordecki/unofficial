package unofficial

import com.google.common.util.concurrent.RateLimiter
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets

val USER_AGENT: String = "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"

val LOGGER = LoggerFactory.getLogger("Main")

interface WebClient {
  fun get(url: String): ByteArray

  fun getHtml(url: String): String =
      get(url).toString(StandardCharsets.UTF_8)
}

class WebClientImpl : WebClient {
  val rateLimiter = RateLimiter.create(0.75)

  override fun get(url: String): ByteArray {
    LOGGER.info("Fetching: $url")
    rateLimiter.acquire()
    val conn = URL(url).openConnection()
    conn.setRequestProperty("User-Agent", USER_AGENT)
    return conn.inputStream.readBytes()
  }
}

class CachingWebClient(val cachePath: File) : WebClient, Closeable {
  val cache = hashMapOf<String, ByteArray>()

  init {
    if (cachePath.exists()) {
      for ((k, v) in ObjectInputStream(cachePath.inputStream()).readObject() as Map<*, *>) {
        cache[k as String] = v as ByteArray
      }
      LOGGER.info("Loaded ${cache.size} pages from HTTP cache")
    }
  }

  val realClient = WebClientImpl()

  override fun get(url: String): ByteArray {
    if (!cache.containsKey(url)) {
      val data = realClient.get(url)
      cache[url] = data
      return data
    } else {
      LOGGER.info("Reading prefetched $url")
      return cache[url]!!
    }
  }

  override fun close() {
    LOGGER.info("Saving ${cache.size} pages to HTTP cache at $cachePath")
    val stream = ObjectOutputStream(cachePath.outputStream())
    stream.writeObject(cache)
    stream.close()
  }
}

class TestResourceWebClient(val cls: Class<*>) : WebClient {
  override fun get(url: String): ByteArray {
    var protocolless = if (url.contains("://")) url.substring(url.indexOf("://") + 3) else url
    protocolless = protocolless.replace("?", "")
    if (cls.getResource(protocolless) == null) {
      throw IllegalArgumentException("Test resource not found: $protocolless")
    }
    return cls.getResourceAsStream(protocolless).readBytes()
  }

  override fun getHtml(url: String): String =
      super.getHtml("$url.html")
}
