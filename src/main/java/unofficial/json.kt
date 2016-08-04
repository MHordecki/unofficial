package unofficial

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object Json {
  fun getObjectMapper(): ObjectMapper {
    val mapper = ObjectMapper()
    mapper.registerModules(KotlinModule(), SerializerModule())
    return mapper
  }
}
