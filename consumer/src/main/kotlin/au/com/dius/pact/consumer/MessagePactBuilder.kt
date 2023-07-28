package au.com.dius.pact.consumer

import au.com.dius.pact.consumer.dsl.DslPart
import au.com.dius.pact.consumer.dsl.Matcher
import au.com.dius.pact.consumer.dsl.MetadataBuilder
import au.com.dius.pact.consumer.xml.PactXmlBuilder
import au.com.dius.pact.core.model.Consumer
import au.com.dius.pact.core.model.ContentType
import au.com.dius.pact.core.model.InvalidPactException
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.Provider
import au.com.dius.pact.core.model.ProviderState
import au.com.dius.pact.core.model.generators.Category
import au.com.dius.pact.core.model.messaging.Message
import au.com.dius.pact.core.model.messaging.MessagePact

/**
 * PACT DSL builder for v3 specification
 */
class MessagePactBuilder @JvmOverloads constructor(
  /**
   * The consumer for the pact.
   */
  private var consumer: Consumer = Consumer(),

  /**
   * The provider for the pact.
   */
  private var provider: Provider = Provider(),

  /**
   * Provider states
   */
  private var providerStates: MutableList<ProviderState> = mutableListOf(),

  /**
   * Messages for the pact
   */
  private var messages: MutableList<Message> = mutableListOf()
) {
  /**
   * Name the provider that the consumer has a pact with.
   *
   * @param provider provider name
   * @return this builder.
   */
  fun hasPactWith(provider: String): MessagePactBuilder {
    this.provider = Provider(provider)
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState description of the provider state
   * @return this builder.
   */
  fun given(providerState: String): MessagePactBuilder {
    this.providerStates.add(ProviderState(providerState))
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState description of the provider state
   * @param params key/value pairs to describe state
   * @return this builder.
   */
  fun given(providerState: String, params: Map<String, Any>): MessagePactBuilder {
    this.providerStates.add(ProviderState(providerState, params))
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState state of the provider
   * @return this builder.
   */
  fun given(providerState: ProviderState): MessagePactBuilder {
    this.providerStates.add(providerState)
    return this
  }

  /**
   * Adds a message expectation in the pact.
   *
   * @param description message description.
   */
  fun expectsToReceive(description: String): MessagePactBuilder {
    messages.add(Message(description, providerStates))
    return this
  }

  /**
   *  Adds the expected metadata to the message
   */
  fun withMetadata(metadata: Map<String, Any>): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    message.metaData = metadata.mapValues { (key, value) ->
      if (value is Matcher) {
        message.matchingRules.addCategory("metadata").addRule(key, value.matcher!!)
        if (value.generator != null) {
          message.generators.addGenerator(category = au.com.dius.pact.core.model.generators.Category.METADATA,
            generator = value.generator!!)
        }
        value.value
      } else {
        value
      }
    }.toMutableMap()
    return this
  }

  /**
   *  Adds the expected metadata to the message using a builder
   */
  fun withMetadata(consumer: java.util.function.Consumer<MetadataBuilder>): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    val metadataBuilder = MetadataBuilder()
    consumer.accept(metadataBuilder)
    message.metaData = metadataBuilder.values
    message.matchingRules.addCategory(metadataBuilder.matchers)
    message.generators.addGenerators(Category.METADATA, metadataBuilder.generators)
    return this
  }

  /**
   * Adds the JSON body as the message content
   */
  fun withContent(body: DslPart): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    val metadata = message.metaData.toMutableMap()
    val contentTypeEntry = metadata.entries.find {
      it.key.toLowerCase() == "contenttype" || it.key.toLowerCase() == "content-type"
    }

    var contentType = ContentType.JSON
    if (contentTypeEntry == null) {
      metadata["contentType"] = contentType.toString()
    } else {
      contentType = ContentType(contentTypeEntry.value.toString())
      metadata.remove(contentTypeEntry.key)
      metadata["contentType"] = contentTypeEntry.value
    }

    val parent = body.close()!!
    message.contents = OptionalBody.body(parent.toString().toByteArray(contentType.asCharset()), contentType)
    message.metaData = metadata
    message.matchingRules.addCategory(parent.matchers)
    message.generators.addGenerators(parent.generators)

    return this
  }

  /**
   * Adds the XML body as the message content
   */
  fun withContent(xmlBuilder: PactXmlBuilder): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    val metadata = message.metaData.toMutableMap()
    val contentTypeEntry = metadata.entries.find {
      it.key.toLowerCase() == "contenttype" || it.key.toLowerCase() == "content-type"
    }

    var contentType = ContentType.XML
    if (contentTypeEntry == null) {
      metadata["contentType"] = contentType.toString()
    } else {
      contentType = ContentType(contentTypeEntry.value.toString())
      metadata.remove(contentTypeEntry.key)
      metadata["contentType"] = contentTypeEntry.value
    }

    message.contents = OptionalBody.body(xmlBuilder.asBytes(contentType.asCharset()), contentType)
    message.metaData = metadata
    message.matchingRules.addCategory(xmlBuilder.matchingRules)
    message.generators.addGenerators(xmlBuilder.generators)

    return this
  }

  /**
   * Adds the text as the message contents
   */
  @JvmOverloads
  fun withContent(contents: String, contentType: String = "text/plain"): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    message.metaData["contentType"] = contentType

    val ct = ContentType(contentType)
    message.contents = OptionalBody.body(contents.toByteArray(ct.asCharset()), ct)

    return this
  }

  /**
   * Convert this builder into a Pact
   */
  fun toPact() = MessagePact(provider, consumer, messages)

  companion object {
    /**
     * Name the consumer of the pact
     *
     * @param consumer Consumer name
     */
    @JvmStatic
    fun consumer(consumer: String) = MessagePactBuilder(Consumer(consumer))
  }
}
