package au.com.dius.pact.core.matchers

import au.com.dius.pact.core.model.ContentType
import kotlin.reflect.full.createInstance

object MatchingConfig {
  val bodyMatchers = mapOf(
    "application/vnd.schemaregistry.v1\\+json" to "au.com.dius.pact.core.matchers.KafkaJsonSchemaContentMatcher",
    "application/.*xml" to "au.com.dius.pact.core.matchers.XmlBodyMatcher",
    "text/xml" to "au.com.dius.pact.core.matchers.XmlBodyMatcher",
    ".*json.*" to "au.com.dius.pact.core.matchers.JsonBodyMatcher",
    "text/plain" to "au.com.dius.pact.core.matchers.PlainTextBodyMatcher",
    "multipart/form-data" to "au.com.dius.pact.core.matchers.MultipartMessageBodyMatcher",
    "multipart/mixed" to "au.com.dius.pact.core.matchers.MultipartMessageBodyMatcher",
    "application/x-www-form-urlencoded" to "au.com.dius.pact.core.matchers.FormPostBodyMatcher"
  )

  @JvmStatic
  fun lookupBodyMatcher(contentType: String?): BodyMatcher? {
    return if (contentType != null) {
      val matcher = bodyMatchers.entries.find { contentType.matches(Regex(it.key)) }?.value
      if (matcher != null) {
        val clazz = Class.forName(matcher).kotlin
        (clazz.objectInstance ?: clazz.createInstance()) as BodyMatcher?
      } else {
        val ct = ContentType(contentType)
        when (ct.override()) {
          "json" -> JsonBodyMatcher
          "text" -> PlainTextBodyMatcher()
          else -> null
        }
      }
    } else {
      null
    }
  }
}
