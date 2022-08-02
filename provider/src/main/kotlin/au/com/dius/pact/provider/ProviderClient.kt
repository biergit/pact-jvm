package au.com.dius.pact.provider

import au.com.dius.pact.core.model.BrokerUrlSource
import au.com.dius.pact.core.model.FileSource
import au.com.dius.pact.core.model.Interaction
import au.com.dius.pact.core.model.PactSource
import au.com.dius.pact.core.model.ProviderState
import au.com.dius.pact.core.model.Request
import au.com.dius.pact.core.model.UrlSource
import au.com.dius.pact.core.pactbroker.PactBrokerResult
import au.com.dius.pact.core.pactbroker.VerificationNotice
import au.com.dius.pact.core.support.Auth
import au.com.dius.pact.core.support.Json
import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyShell
import mu.KLogging
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpOptions
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpTrace
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.util.EntityUtils
import scala.Function1
import java.io.File
import java.lang.Boolean.getBoolean
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.UnsupportedCharsetException
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function
import au.com.dius.pact.core.model.ContentType as PactContentType

interface IHttpClientFactory {
  fun newClient(provider: IProviderInfo): CloseableHttpClient
}

interface IProviderInfo {
  var protocol: String
  var host: Any?
  var port: Any?
  var path: String
  var name: String

  val requestFilter: Any?
  val stateChangeRequestFilter: Any?
  val stateChangeUrl: URL?
  val stateChangeUsesBody: Boolean
  val stateChangeTeardown: Boolean
  var packagesToScan: List<String>
  var verificationType: PactVerification?
  var createClient: Any?

  var insecure: Boolean
  var trustStore: File?
  var trustStorePassword: String?

  var consumers: MutableList<IConsumerInfo>
}

interface IConsumerInfo {
  var name: String
  var stateChange: Any?
  var stateChangeUsesBody: Boolean
  var packagesToScan: List<String>
  var verificationType: PactVerification?
  var pactSource: Any?
  @Deprecated("Replaced with auth")
  var pactFileAuthentication: List<Any?>
  val notices: List<VerificationNotice>
  val pending: Boolean
  val wip: Boolean
  val auth: Auth?

  fun toPactConsumer(): au.com.dius.pact.core.model.Consumer
  fun resolvePactSource(): PactSource?
}

@Suppress("LongParameterList")
open class ConsumerInfo @JvmOverloads constructor (
  override var name: String = "",
  override var stateChange: Any? = null,
  override var stateChangeUsesBody: Boolean = true,
  override var packagesToScan: List<String> = emptyList(),
  override var verificationType: PactVerification? = null,
  override var pactSource: Any? = null,
  @Deprecated("replaced with auth")
  override var pactFileAuthentication: List<Any?> = emptyList(),
  override val notices: List<VerificationNotice> = emptyList(),
  override val pending: Boolean = false,
  override val wip: Boolean = false,
  override val auth: Auth? = null
) : IConsumerInfo {

  override fun toPactConsumer() = au.com.dius.pact.core.model.Consumer(name)
  override fun resolvePactSource() = Companion.resolvePactSource(pactSource)

  var stateChangeUrl: URL?
    get() = if (stateChange != null) URL(stateChange.toString()) else null
    set(value) { stateChange = value }

  override fun toString(): String {
    return "ConsumerInfo(name='$name', stateChange=$stateChange, stateChangeUsesBody=$stateChangeUsesBody, " +
      "packagesToScan=$packagesToScan, verificationType=$verificationType, pactSource=$pactSource, " +
      "notices=$notices, pending=$pending, wip=$wip)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ConsumerInfo

    if (name != other.name) return false
    if (stateChange != other.stateChange) return false
    if (stateChangeUsesBody != other.stateChangeUsesBody) return false
    if (packagesToScan != other.packagesToScan) return false
    if (verificationType != other.verificationType) return false
    if (pactSource != other.pactSource) return false
    if (pactFileAuthentication != other.pactFileAuthentication) return false
    if (notices != other.notices) return false
    if (pending != other.pending) return false
    if (wip != other.wip) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (stateChange?.hashCode() ?: 0)
    result = 31 * result + stateChangeUsesBody.hashCode()
    result = 31 * result + packagesToScan.hashCode()
    result = 31 * result + (verificationType?.hashCode() ?: 0)
    result = 31 * result + (pactSource?.hashCode() ?: 0)
    result = 31 * result + pactFileAuthentication.hashCode()
    result = 31 * result + notices.hashCode()
    result = 31 * result + pending.hashCode()
    result = 31 * result + wip.hashCode()
    return result
  }

  companion object : KLogging() {
    fun from(result: PactBrokerResult) =
      ConsumerInfo(name = result.name,
        pactSource = BrokerUrlSource(url = result.source, pactBrokerUrl = result.pactBrokerUrl, result = result),
        pactFileAuthentication = result.pactFileAuthentication, notices = result.notices, pending = result.pending,
        wip = result.wip, auth = result.auth
      )

    /**
     * Resolves the source by looking at the type. If it is a callable object, will invoke that first.
     */
    fun resolvePactSource(source: Any?): PactSource? {
      val result = when (source) {
        is Callable<*> -> source.call()
        else -> source
      }
      return when (result) {
        is PactSource -> result
        is File -> FileSource<Interaction>(result)
        is URL -> UrlSource<Interaction>(result.toString())
        is URI -> UrlSource<Interaction>(result.toString())
        else -> {
          logger.warn { "Expected a PactSource, but got $source (${source?.javaClass})" }
          null
        }
      }
    }
  }
}

data class ProviderResponse @JvmOverloads constructor(
  val statusCode: Int,
  val headers: Map<String, List<String>> = emptyMap(),
  val contentType: au.com.dius.pact.core.model.ContentType = au.com.dius.pact.core.model.ContentType.UNKNOWN,
  val body: String? = null
)

/**
 * Client HTTP utility for providers
 */
@Suppress("TooManyFunctions")
open class ProviderClient(
  val provider: IProviderInfo,
  private val httpClientFactory: IHttpClientFactory
) {

  companion object : KLogging() {
    const val CONTENT_TYPE = "Content-Type"
    const val UTF8 = "UTF-8"
    const val REQUEST = "request"
    const val ACTION = "action"

    val SINGLE_VALUE_HEADERS = setOf("set-cookie", "www-authenticate", "proxy-authenticate", "date", "expires",
      "last-modified", "if-modified-since", "if-unmodified-since", "retry-after")

    private fun invokeIfClosure(property: Any?) = if (property is Closure<*>) {
      property.call()
    } else {
      property
    }

    private fun convertToInteger(port: Any?) = if (port is Number) {
      port.toInt()
    } else {
      Integer.parseInt(port.toString())
    }

    @JvmStatic
    fun urlEncodedFormPost(request: Request) = request.method.toLowerCase() == "post" &&
      request.determineContentType().getBaseType() == ContentType.APPLICATION_FORM_URLENCODED.mimeType

    fun isFunctionalInterface(requestFilter: Any) =
      requestFilter::class.java.interfaces.any { it.isAnnotationPresent(FunctionalInterface::class.java) }

    @JvmStatic
    fun stripTrailingSlash(basePath: String): String {
      return when {
        basePath == "/" -> ""
        basePath.isNotEmpty() && basePath.last() == '/' -> basePath.substring(0, basePath.length - 1)
        else -> basePath
      }
    }
  }

  open fun makeRequest(request: Request): ProviderResponse {
    val httpclient = getHttpClient()
    val method = prepareRequest(request)
    return executeRequest(httpclient, method)
  }

  open fun executeRequest(httpclient: CloseableHttpClient, method: HttpUriRequest): ProviderResponse {
    return httpclient.execute(method).use {
      handleResponse(it)
    }
  }

  open fun prepareRequest(request: Request): HttpUriRequest {
    logger.debug { "Making request for provider $provider:" }
    logger.debug { request.toString() }

    val method = newRequest(request)
    setupHeaders(request, method)
    setupBody(request, method)

    executeRequestFilter(method)

    return method
  }

  open fun executeRequestFilter(method: HttpRequest) {
    val requestFilter = provider.requestFilter
    if (requestFilter != null) {
      when (requestFilter) {
        is Closure<*> -> requestFilter.call(method)
        is Function1<*, *> -> (requestFilter as Function1<HttpRequest, *>).apply(method)
        is org.apache.commons.collections4.Closure<*> ->
          (requestFilter as org.apache.commons.collections4.Closure<Any>).execute(method)
        else -> {
          if (isFunctionalInterface(requestFilter)) {
            invokeJavaFunctionalInterface(requestFilter, method)
          } else {
            val binding = Binding()
            binding.setVariable(REQUEST, method)
            val shell = GroovyShell(binding)
            shell.evaluate(requestFilter as String)
          }
        }
      }
    }
  }

  private fun invokeJavaFunctionalInterface(functionalInterface: Any, httpRequest: HttpRequest) {
    when (functionalInterface) {
      is Consumer<*> -> (functionalInterface as Consumer<HttpRequest>).accept(httpRequest)
      is Function<*, *> -> (functionalInterface as Function<HttpRequest, Any?>).apply(httpRequest)
      is Callable<*> -> (functionalInterface as Callable<HttpRequest>).call()
      else -> throw IllegalArgumentException("Java request filters must be either a Consumer or Function that " +
        "takes at least one HttpRequest parameter")
    }
  }

  open fun setupBody(request: Request, method: HttpRequest) {
    if (method is HttpEntityEnclosingRequest && request.body.isPresent()) {
      val contentTypeHeader = request.contentTypeHeader()
      if (null != contentTypeHeader) {
        try {
          val contentType = ContentType.parse(contentTypeHeader)
          method.entity = StringEntity(request.body.valueAsString(), contentType)
        } catch (e: UnsupportedCharsetException) {
          method.entity = StringEntity(request.body.valueAsString())
        }
      } else {
        method.entity = StringEntity(request.body.valueAsString())
      }
    }
  }

  open fun setupHeaders(request: Request, method: HttpRequest) {
    val headers = request.headers
    if (headers.isNotEmpty()) {
      headers.forEach { (key, value) ->
        method.addHeader(key, value.joinToString(", "))
      }
    }

    if (!method.containsHeader(CONTENT_TYPE) && request.body.isPresent()) {
      val contentType = when (request.body.contentType) {
        PactContentType.UNKNOWN -> "text/plain; charset=ISO-8859-1"
        else -> request.body.contentType.toString()
      }
      method.addHeader(CONTENT_TYPE, contentType)
    }
  }

  open fun makeStateChangeRequest(
    stateChangeUrl: Any?,
    state: ProviderState,
    postStateInBody: Boolean,
    isSetup: Boolean,
    stateChangeTeardown: Boolean
  ): CloseableHttpResponse? {
    return if (stateChangeUrl != null) {
      val httpclient = getHttpClient()
      val urlBuilder = if (stateChangeUrl is URI) {
        URIBuilder(stateChangeUrl)
      } else {
        URIBuilder(stateChangeUrl.toString())
      }
      val method: HttpPost?

      if (postStateInBody) {
        method = HttpPost(urlBuilder.build())
        val map = mutableMapOf<String, Any>("state" to state.name.toString())
        if (state.params.isNotEmpty()) {
          map["params"] = state.params
        }
        if (stateChangeTeardown) {
          map["action"] = if (isSetup) "setup" else "teardown"
        }
        method.entity = StringEntity(Json.prettyPrint(map), ContentType.APPLICATION_JSON)
      } else {
        urlBuilder.setParameter("state", state.name)
        state.params.forEach { (k, v) -> urlBuilder.setParameter(k, v.toString()) }
        if (stateChangeTeardown) {
          if (isSetup) {
            urlBuilder.setParameter(ACTION, "setup")
          } else {
            urlBuilder.setParameter(ACTION, "teardown")
          }
        }
        method = HttpPost(urlBuilder.build())
      }

      if (provider.stateChangeRequestFilter != null) {
        when (provider.stateChangeRequestFilter) {
          is Closure<*> -> (provider.stateChangeRequestFilter as Closure<*>).call(method)
          is Function1<*, *> -> (provider.stateChangeRequestFilter as Function1<Any, Any>).apply(method)
          else -> {
            val binding = Binding()
            binding.setVariable(REQUEST, method)
            val shell = GroovyShell(binding)
            shell.evaluate(provider.stateChangeRequestFilter.toString())
          }
        }
      }

      httpclient.execute(method)
    } else {
      null
    }
  }

  fun getHttpClient() = httpClientFactory.newClient(provider)

  fun handleResponse(httpResponse: HttpResponse): ProviderResponse {
    logger.debug { "Received response: ${httpResponse.statusLine}" }

    var contentType = PactContentType.TEXT_PLAIN
    val headers = httpResponse.allHeaders
      .groupBy({ header -> header.name }, { header ->
        if (SINGLE_VALUE_HEADERS.contains(header.name.toLowerCase())) {
          listOf(header.value.trim())
        } else {
          header.value.split(',').map { it.trim() }
        }
      })
      .mapValues { it.value.flatten() }

    var body: String? = null
    val entity = httpResponse.entity
    if (entity != null) {
      if (entity.contentType != null) {
        contentType = PactContentType.fromString(entity.contentType.value)
      }
      body = EntityUtils.toString(entity, contentType.asCharset())
    }

    val response = ProviderResponse(
      httpResponse.statusLine.statusCode,
      headers,
      contentType,
      body
    )

    logger.debug { "Response: $response" }

    return response
  }

  open fun newRequest(request: Request): HttpUriRequest {
    val scheme = provider.protocol
    val host = invokeIfClosure(provider.host)
    val port = convertToInteger(invokeIfClosure(provider.port))
    var path = stripTrailingSlash(provider.path)

    var urlBuilder = URIBuilder()
    if (systemPropertySet("pact.verifier.disableUrlPathDecoding")) {
      path += request.path
      urlBuilder = URIBuilder("$scheme://$host:$port$path")
    } else {
      path += URLDecoder.decode(request.path, UTF8)
      urlBuilder.scheme = provider.protocol
      urlBuilder.host = invokeIfClosure(provider.host)?.toString()
      urlBuilder.port = convertToInteger(invokeIfClosure(provider.port))
      urlBuilder.path = path
    }

    if (request.query != null) {
      request.query.forEach { entry ->
        entry.value.forEach {
          urlBuilder.addParameter(entry.key, it)
        }
      }
    }

    val url = urlBuilder.build().toString()
    return when (request.method.toLowerCase()) {
      "post" -> HttpPost(url)
      "put" -> HttpPut(url)
      "options" -> HttpOptions(url)
      "delete" -> HttpDeleteWithEntity(url)
      "head" -> HttpHead(url)
      "patch" -> HttpPatch(url)
      "trace" -> HttpTrace(url)
      else -> HttpGet(url)
    }
  }

  open fun systemPropertySet(property: String) = getBoolean(property)

  internal class HttpDeleteWithEntity(uri: String) : HttpEntityEnclosingRequestBase() {
    init {
      setURI(URI.create(uri))
    }

    override fun getMethod(): String {
      return HttpDelete.METHOD_NAME
    }
  }
}
