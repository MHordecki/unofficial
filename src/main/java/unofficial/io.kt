package unofficial

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import java.io.File
import java.util.*

interface Io {
  fun read(path: String): ByteArray
  fun write(path: String, bytes: ByteArray)
  fun copy(source: String, dest: String) {
    write(dest, read(source))
  }
}

class InMemoryIo() : Io {
  val files = HashMap<String, ByteArray>()

  override fun read(path: String): ByteArray {
    if (!files.containsKey(path)) {
      throw IllegalArgumentException("Unknown path: $path")
    }
    return files[path]!!
  }

  override fun write(path: String, bytes: ByteArray) {
    LOGGER.info("InMemoryIo#write: $path with ${bytes.size} bytes")
    files[path] = bytes
  }
}


class GcsIo(val storage: Storage, val bucket: String) : Io {
  override fun read(path: String): ByteArray =
      storage.readAllBytes(bucket, path)

  override fun write(path: String, bytes: ByteArray) {
    LOGGER.info("GcsIo#write: $bucket:$path with ${bytes.size} bytes")
    var builder = BlobInfo.builder(bucket, path)
    when {
      path.endsWith(".html") -> builder = builder.contentType("text/html")
      path.endsWith(".json") -> builder = builder.contentType("application/json")
      path.endsWith(".txt") -> builder = builder.contentType("text/plain")
      path.endsWith(".png") -> builder = builder.contentType("image/png")
      path.endsWith(".css") -> builder = builder.contentType("text/css")
      path.endsWith(".js") -> builder = builder.contentType("text/javascript")
    }
    storage.create(builder.build(), bytes)
  }
}

class DiskIo(val rootPath: String) : Io {
  override fun read(path: String): ByteArray =
      File("$rootPath/$path").readBytes()

  override fun write(path: String, bytes: ByteArray) {
    LOGGER.info("DiskIo#write: $path with ${bytes.size} bytes")
    val file = File("$rootPath/$path")
    file.parentFile.mkdirs()
    File("$rootPath/$path").writeBytes(bytes)
  }
}


