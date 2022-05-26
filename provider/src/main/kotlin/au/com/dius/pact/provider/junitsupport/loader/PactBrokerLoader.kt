package au.com.dius.pact.provider.junitsupport.loader

import au.com.dius.pact.core.matchers.util.padTo
import au.com.dius.pact.core.model.BrokerUrlSource
import au.com.dius.pact.core.model.DefaultPactReader
import au.com.dius.pact.core.model.Interaction
import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.PactBrokerSource
import au.com.dius.pact.core.model.PactReader
import au.com.dius.pact.core.model.PactSource
import au.com.dius.pact.core.pactbroker.ConsumerVersionSelector
import au.com.dius.pact.core.pactbroker.IPactBrokerClient
import au.com.dius.pact.core.pactbroker.PactBrokerClient
import au.com.dius.pact.core.pactbroker.PactBrokerClientConfig
import au.com.dius.pact.core.support.Utils.permutations
import au.com.dius.pact.core.support.expressions.DataType
import au.com.dius.pact.core.support.expressions.ExpressionParser.parseExpression
import au.com.dius.pact.core.support.expressions.ExpressionParser.parseListExpression
import au.com.dius.pact.core.support.expressions.SystemPropertyResolver
import au.com.dius.pact.core.support.expressions.ValueResolver
import au.com.dius.pact.core.support.isNotEmpty
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import mu.KLogging
import org.apache.http.client.utils.URIBuilder
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlin.reflect.KClass

data class SelectorResult(
  val selectors: List<ConsumerVersionSelector>,
  val filtered: Boolean
)

/**
 * Out-of-the-box implementation of {@link PactLoader} that downloads pacts from Pact broker
 */
@Suppress("LongParameterList", "TooManyFunctions")
open class PactBrokerLoader(
  @Deprecated("Use pactBrokerUrl")
  val pactBrokerHost: String?,
  @Deprecated("Use pactBrokerUrl")
  val pactBrokerPort: String?,
  @Deprecated("Use pactBrokerUrl")
  val pactBrokerScheme: String?,
  @Deprecated(message = "Use Consumer version selectors instead",
    replaceWith = ReplaceWith("pactBrokerConsumerVersionSelectors"))
  val pactBrokerTags: List<String>? = listOf("latest"),
  val pactBrokerConsumerVersionSelectors: List<VersionSelector>,
  val pactBrokerConsumers: List<String> = emptyList(),
  var failIfNoPactsFound: Boolean = true,
  var authentication: PactBrokerAuth?,
  var valueResolverClass: KClass<out ValueResolver>?,
  valueResolver: ValueResolver? = null,
  val enablePendingPacts: String = "false",
  val providerTags: List<String> = emptyList(),
  val providerBranches: List<String> = emptyList(),
  val includeWipPactsSince: String = "",
  val pactBrokerUrl: String? = null,
  val enableInsecureTls: String = "false"
) : OverrideablePactLoader {

  private var resolver: ValueResolver? = valueResolver
  private var overriddenPactUrl: String? = null
  private var overriddenConsumer: String? = null

  var pactReader: PactReader = DefaultPactReader

  constructor(pactBroker: PactBroker) : this(
    pactBroker.host,
    pactBroker.port,
    pactBroker.scheme,
    pactBroker.tags.toList(),
    pactBroker.consumerVersionSelectors.toList(),
    pactBroker.consumers.toList(),
    true,
    pactBroker.authentication,
    pactBroker.valueResolver,
    null,
    pactBroker.enablePendingPacts,
    pactBroker.providerTags.toList(),
    pactBroker.providerBranches.toList(),
    pactBroker.includeWipPactsSince,
    pactBroker.url,
    pactBroker.enableInsecureTls
  )

  override fun description(): String {
    val resolver = setupValueResolver()
    val consumerVersionSelectors = buildConsumerVersionSelectors(resolver)
    val consumers = pactBrokerConsumers.flatMap { parseListExpression(it, resolver) }.filter { it.isNotEmpty() }
    var source = getPactBrokerSource(resolver).description()
    if (!consumerVersionSelectors.isNullOrEmpty()) {
      source += " consumerVersionSelectors=$consumerVersionSelectors"
    }
    if (consumers.isNotEmpty()) {
      source += " consumers=$consumers"
    }
  return source
  }

  override fun overridePactUrl(pactUrl: String, consumer: String) {
    overriddenPactUrl = pactUrl
    overriddenConsumer = consumer
  }

  @Throws(IOException::class)
  override fun load(providerName: String): List<Pact<*>> {
    val resolver = setupValueResolver()
    return when {
      overriddenPactUrl.isNotEmpty() -> {
        val brokerUri = brokerUrl(resolver).build()
        val pactBrokerClient = newPactBrokerClient(brokerUri, resolver)
        val pactSource = BrokerUrlSource(overriddenPactUrl!!, brokerUri.toString(), options = pactBrokerClient.options)
        pactSource.encodePath = false
        listOf(pactReader.loadPact(pactSource, pactBrokerClient.options))
      }
      else -> {
        try {
          val consumerVersionSelectors = buildConsumerVersionSelectors(resolver)
          loadPactsForProvider(providerName, consumerVersionSelectors, resolver)
        } catch (e: NoPactsFoundException) {
          // Ignoring exception at this point, it will be handled at a higher level
          emptyList<Pact<Interaction>>()
        }
      }
    }
  }

  fun buildConsumerVersionSelectors(resolver: ValueResolver): List<ConsumerVersionSelector> {
    val tags = pactBrokerTags.orEmpty().flatMap { parseListExpression(it, resolver) }
    return if (shouldFallBackToTags(tags, pactBrokerConsumerVersionSelectors, resolver)) {
      permutations(tags, pactBrokerConsumers.flatMap { parseListExpression(it, resolver) })
        .map { ConsumerVersionSelector(it.first, consumer = it.second) }
    } else {
      pactBrokerConsumerVersionSelectors.flatMap {
        val tags = parseListExpression(it.tag, resolver)
        val consumers = parseListExpression(it.consumer, resolver)
        val fallbackTag = parseExpression(it.fallbackTag, DataType.STRING, resolver) as String?
        val parsedLatest = parseListExpression(it.latest, resolver)
        val latest = when {
          parsedLatest.isEmpty() -> List(tags.size) { true.toString() }
          parsedLatest.size == 1 -> parsedLatest.padTo(tags.size, parsedLatest[0])
          else -> parsedLatest
        }

        if (tags.size != latest.size) {
          throw IllegalArgumentException("Invalid Consumer version selectors. Each version selector must have a tag " +
                  "and latest property")
        }

        when {
          tags.isNotEmpty() && consumers.isNotEmpty() -> {
            permutations(tags.mapIndexed { index, tag -> tag to index  }, consumers).map { (tag, consumer) ->
              ConsumerVersionSelector(tag!!.first, latest[tag.second].toBoolean(), fallbackTag = fallbackTag,
                consumer = consumer)
            }
          }
          tags.isNotEmpty() -> {
            tags.mapIndexed { index, tag ->
              ConsumerVersionSelector(tag, latest[index].toBoolean(), fallbackTag = fallbackTag,
                consumer = consumers.firstOrNull())
            }
          }
          consumers.isNotEmpty() -> {
            consumers.map { name ->
              ConsumerVersionSelector(null, true, fallbackTag = fallbackTag, consumer = name)
            }
          }
          else -> listOf()
        }
      }
    }
  }

  fun shouldFallBackToTags(tags: List<String>, selectors: List<VersionSelector>, resolver: ValueResolver): Boolean {
    return selectors.isEmpty() || (selectors.size == 1 && parseListExpression(selectors[0].tag, resolver).isEmpty() &&
      tags.isNotEmpty())
  }

  private fun setupValueResolver(): ValueResolver {
    var valueResolver: ValueResolver = SystemPropertyResolver
    if (resolver != null) {
      valueResolver = resolver!!
    } else if (valueResolverClass != null) {
      if (valueResolverClass!!.objectInstance != null) {
        valueResolver = valueResolverClass!!.objectInstance!!
      } else {
        try {
          valueResolver = valueResolverClass!!.java.newInstance()
        } catch (e: InstantiationException) {
          logger.warn(e) { "Failed to instantiate the value resolver, using the default" }
        } catch (e: IllegalAccessException) {
          logger.warn(e) { "Failed to instantiate the value resolver, using the default" }
        }
      }
    }
    return valueResolver
  }

  override fun getPactSource(): PactSource? {
    val resolver = setupValueResolver()
    return getPactBrokerSource(resolver)
  }

  override fun setValueResolver(valueResolver: ValueResolver) {
    this.resolver = valueResolver
  }

  @Throws(IOException::class, IllegalArgumentException::class)
  @Suppress("ThrowsCount")
  private fun loadPactsForProvider(
    providerName: String,
    selectors: List<ConsumerVersionSelector>,
    resolver: ValueResolver
  ): List<Pact<*>> {
    logger.debug { "Loading pacts from pact broker for provider $providerName and consumer version selectors " +
      "$selectors" }
    val pending = parseExpression(enablePendingPacts, DataType.BOOLEAN, resolver) as Boolean
    val providerTags = providerTags.flatMap { parseListExpression(it, resolver) }.filter { it.isNotEmpty() }
    val providerBranches = providerBranches.flatMap { parseListExpression(it, resolver) }.filter { it.isNotEmpty() }
    if (pending && providerTags.none { it.isNotEmpty() } && providerBranches.none { it.isNotEmpty() }) {
      throw IllegalArgumentException("Pending pacts feature has been enabled, but no provider tags or branches have " +
        "been specified. To use the pending pacts feature, you need to provide the list of provider names for the " +
        "provider application version with the providerTags or providerBranches property that will be published with " +
        "the verification results.")
    }
    val wipSinceDate = if (pending) parseExpression(includeWipPactsSince, DataType.STRING, resolver) as String else ""

    val uriBuilder = brokerUrl(resolver)
    try {
      val pactBrokerClient = newPactBrokerClient(uriBuilder.build(), resolver)

      val result = pactBrokerClient.fetchConsumersWithSelectors(providerName, selectors, providerTags,
              providerBranches, pending, wipSinceDate)
      var consumers = when (result) {
        is Ok -> result.value
        is Err -> throw result.error
      }

      if (failIfNoPactsFound && consumers.isEmpty()) {
        throw NoPactsFoundException("No consumer pacts were found for provider '" + providerName + "' and consumer " +
          "version selectors '" + selectors + "'. (URL " + getUrlForProvider(providerName, pactBrokerClient) + ")")
      }

      if (pactBrokerConsumers.isNotEmpty()) {
        val consumerInclusions = pactBrokerConsumers.flatMap { parseListExpression(it, resolver) }
        consumers = consumers.filter { it.usedNewEndpoint || consumerInclusions.isEmpty() ||
          consumerInclusions.contains(it.name) }
      }

      return consumers.map { pactReader.loadPact(it, pactBrokerClient.options) }
    } catch (e: URISyntaxException) {
      throw IOException("Was not able load pacts from broker as the broker URL was invalid", e)
    }
  }

  fun brokerUrl(resolver: ValueResolver): URIBuilder {
    val (host, port, scheme, _, url) = getPactBrokerSource(resolver)

    return if (url.isNullOrEmpty()) {
      val uriBuilder = URIBuilder().setScheme(scheme).setHost(host)
      if (port.isNotEmpty()) {
        uriBuilder.port = Integer.parseInt(port)
      }
      uriBuilder
    } else {
      URIBuilder(url)
    }
  }

  fun getPactBrokerSource(resolver: ValueResolver): PactBrokerSource<Interaction> {
    val scheme = parseExpression(pactBrokerScheme, DataType.RAW, resolver)?.toString()
    val host = parseExpression(pactBrokerHost, DataType.RAW, resolver)?.toString()
    val port = parseExpression(pactBrokerPort, DataType.RAW, resolver)?.toString()
    val url = parseExpression(pactBrokerUrl, DataType.RAW, resolver)?.toString()

    return if (url.isNullOrEmpty()) {
      if (host.isNullOrEmpty() || !host.matches(Regex("[0-9a-zA-Z\\-.]+"))) {
        throw IllegalArgumentException(String.format("Invalid pact broker host specified ('%s'). " +
          "Please provide a valid host or specify the system property 'pactbroker.host'.", pactBrokerHost))
      }

      if (port.isNotEmpty() && !port!!.matches(Regex("^[0-9]+"))) {
        throw IllegalArgumentException(String.format("Invalid pact broker port specified ('%s'). " +
          "Please provide a valid port number or specify the system property 'pactbroker.port'.", pactBrokerPort))
      }

      if (scheme == null) {
        PactBrokerSource(host, port)
      } else {
        PactBrokerSource(host, port, scheme)
      }
    } else {
      PactBrokerSource(null, null, url = url)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun getUrlForProvider(providerName: String, pactBrokerClient: IPactBrokerClient): String {
    return try {
      pactBrokerClient.getUrlForProvider(providerName, "") ?: "Unknown"
    } catch (e: Exception) {
      logger.debug(e) { "Failed to get provider URL from the pact broker" }
      "Unknown"
    }
  }

  open fun newPactBrokerClient(url: URI, resolver: ValueResolver): IPactBrokerClient {
    var options = mapOf<String, Any>()
    val insecureTls = parseExpression(enableInsecureTls, DataType.BOOLEAN, resolver) as Boolean
    val config = PactBrokerClientConfig(insecureTLS = insecureTls)

    if (authentication == null) {
      logger.debug { "Authentication: None" }
    } else {
      val username = parseExpression(authentication!!.username, DataType.RAW, resolver)?.toString()
      val token = parseExpression(authentication!!.token, DataType.RAW, resolver)?.toString()

      // Check if username is set. If yes, use basic auth.
      if (username.isNotEmpty()) {
        logger.debug { "Authentication: Basic" }
        options = mapOf(
          "authentication" to listOf(
            "basic", username,
            parseExpression(authentication!!.password, DataType.RAW, resolver)
          )
        )
      // Check if token is set. If yes, use bearer auth.
      } else if (token.isNotEmpty()) {
        logger.debug { "Authentication: Bearer" }
        options = mapOf("authentication" to listOf("bearer", token))
      }
    }

    return PactBrokerClient(url.toString(), options.toMutableMap(), config)
  }

  companion object : KLogging()
}
