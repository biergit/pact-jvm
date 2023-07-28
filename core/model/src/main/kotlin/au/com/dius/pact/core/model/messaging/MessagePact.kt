package au.com.dius.pact.core.model.messaging

import au.com.dius.pact.core.model.BasePact
import au.com.dius.pact.core.model.Consumer
import au.com.dius.pact.core.model.Interaction
import au.com.dius.pact.core.model.InvalidPactException
import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.DefaultPactReader
import au.com.dius.pact.core.model.PactSource
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.Provider
import au.com.dius.pact.core.model.UnknownPactSource
import au.com.dius.pact.core.support.Json
import au.com.dius.pact.core.support.Json.extractFromJson
import au.com.dius.pact.core.support.Utils.extractFromMap
import au.com.dius.pact.core.support.json.JsonParser
import au.com.dius.pact.core.support.json.JsonValue
import au.com.dius.pact.core.support.jsonObject
import mu.KLogging
import java.io.File

/**
 * Pact for a sequences of messages
 */
class MessagePact @JvmOverloads constructor (
  override var provider: Provider,
  override var consumer: Consumer,
  var messages: MutableList<Message> = mutableListOf(),
  override val metadata: Map<String, Any?> = DEFAULT_METADATA,
  override val source: PactSource = UnknownPactSource
) : BasePact<Message>(consumer, provider, metadata, source) {

  override fun toMap(pactSpecVersion: PactSpecVersion): Map<String, Any> {
    if (pactSpecVersion < PactSpecVersion.V3) {
      throw InvalidPactException("Message pacts only support version 3+, cannot write pact specification " +
        "version $pactSpecVersion")
    }
    return mapOf(
      "consumer" to mapOf("name" to consumer.name),
      "provider" to mapOf("name" to provider.name),
      "messages" to messages.map { it.toMap(pactSpecVersion) },
      "metadata" to metaData(jsonObject(metadata.entries.map { it.key to Json.toJson(it.value) }), pactSpecVersion)
    )
  }

  fun mergePacts(pact: Map<String, Any>, pactFile: File): Map<String, Any> {
    val newPact = pact.toMutableMap()
    val json = pactFile.bufferedReader().use { JsonParser.parseReader(it) }

    val pactSpec = "pact-specification"
    val version = extractFromJson(json, "metadata", pactSpec, "version")
    val pactVersion = extractFromMap(pact, "metadata", pactSpec, "version")
    if (version != null && version != pactVersion) {
      throw InvalidPactException("Could not merge pact into '$pactFile': pact specification version is " +
        "$pactVersion, while the file is version $version")
    }

    if (json is JsonValue.Object && json.has("interactions")) {
      throw InvalidPactException("Could not merge pact into '$pactFile': file is not a message pact " +
        "(it contains request/response interactions)")
    }

    val messages = (newPact["messages"] as List<Map<String, Any>>) +
      (json["messages"].asArray()?.values?.map { Json.toMap(it) } ?: emptyList())
      .distinctBy { it["description"] }
    newPact["messages"] = messages
    return newPact
  }

  override fun mergeInteractions(interactions: List<*>) {
    interactions as List<Message>
    messages = (messages + interactions).distinctBy { it.uniqueKey() }.toMutableList()
    sortInteractions()
  }

  override val interactions: List<Message>
    get() = messages

  override fun sortInteractions(): Pact<Message> {
    messages.sortBy { message -> message.providerStates.joinToString { it.name.toString() } + message.description }
    return this
  }

  fun mergePact(other: Pact<out Interaction>): MessagePact {
    if (other !is MessagePact) {
      throw InvalidPactException("Unable to merge pact $other as it is not a MessagePact")
    }
    mergeInteractions(other.interactions)
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as MessagePact

    if (provider != other.provider) return false
    if (consumer != other.consumer) return false
    if (messages != other.messages) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + provider.hashCode()
    result = 31 * result + consumer.hashCode()
    result = 31 * result + messages.hashCode()
    return result
  }

  override fun toString(): String {
    return "MessagePact(provider=$provider, consumer=$consumer, messages=$messages, metadata=$metadata)"
  }

  companion object : KLogging() {
    fun fromJson(json: JsonValue.Object, source: PactSource = UnknownPactSource): MessagePact {
      val transformedJson = DefaultPactReader.transformJson(json)
      val consumer = Consumer.fromJson(transformedJson["consumer"])
      val provider = Provider.fromJson(transformedJson["provider"])
      val messages = transformedJson["messages"].asArray()?.values?.map {
        Message.fromJson(it.downcast())
      } ?: emptyList()
      val metadata = if (transformedJson.has("metadata"))
        Json.toMap(transformedJson["metadata"])
      else emptyMap()
      return MessagePact(provider, consumer, messages.toMutableList(), metadata, source)
    }
  }
}
