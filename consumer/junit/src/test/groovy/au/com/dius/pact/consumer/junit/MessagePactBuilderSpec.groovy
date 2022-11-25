package au.com.dius.pact.consumer.junit

import au.com.dius.pact.consumer.MessagePactBuilder
import au.com.dius.pact.consumer.dsl.Matchers
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.xml.PactXmlBuilder
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.generators.DateTimeGenerator
import au.com.dius.pact.core.model.messaging.Message
import au.com.dius.pact.core.model.ProviderState
import groovy.json.JsonSlurper
import groovy.xml.XmlParser
import groovy.xml.XmlSlurper
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class MessagePactBuilderSpec extends Specification {

  def 'builder should close the DSL objects correctly'() {
    given:
    PactDslJsonBody getBody = new PactDslJsonBody()
    getBody
      .object('metadata')
        .stringType('messageId', 'test')
        .stringType('date', 'test')
        .stringType('contractVersion', 'test')
      .closeObject()
      .object('payload')
        .stringType('name', 'srm.countries.get')
        .stringType('iri', 'some_iri')
      .closeObject()
    .closeObject()

    Map<String, String> metadata = [
      'contentType': 'application/json',
      'destination': Matchers.regexp(~/\w+\d+/, 'X001')
    ]

    MessagePactBuilder builder = MessagePactBuilder.consumer('MessagePactBuilderSpec')
    builder.given('srm.countries.get_message')
      .expectsToReceive('srm.countries.get')
      .withContent(getBody)
      .withMetadata(metadata)

    when:
    def pact = builder.toPact()
    Message message = pact.interactions.first()
    def messageBody = new JsonSlurper().parseText(message.contents.valueAsString())
    def messageMetadata = message.metaData

    then:
    messageBody == [
      metadata: [
        date: 'test',
        messageId: 'test',
        contractVersion: 'test'
      ],
      payload: [
        iri: 'some_iri',
        name: 'srm.countries.get'
      ]
    ]
    messageMetadata == [contentType: 'application/json', destination: 'X001']
    message.matchingRules.rules.body.matchingRules.keySet() == [
      '$.metadata.messageId', '$.metadata.date', '$.metadata.contractVersion', '$.payload.name', '$.payload.iri'
    ] as Set
    message.matchingRules.rules.metadata.matchingRules.keySet() == [
      'destination'
    ] as Set
  }

  @Unroll
  def 'only set the content type if it has not already been set'() {
    given:
    def body = new PactDslJsonBody()
      .object('payload')
        .stringType('name', 'srm.countries.get')
        .stringType('iri', 'some_iri')
      .closeObject()

    Map<String, String> metadata = [
      (contentTypeAttr): 'application/json'
    ]

    when:
    def pact = MessagePactBuilder
      .consumer('MessagePactBuilderSpec')
      .given('srm.countries.get_message')
      .expectsToReceive('srm.countries.get')
      .withMetadata(metadata)
      .withContent(body).toPact()
    Message message = pact.interactions.first()
    def messageMetadata = message.metaData

    then:
    messageMetadata == [contentType: 'application/json']

    where:

    contentTypeAttr << ['contentType', 'contenttype', 'Content-Type', 'content-type']
  }

  @Issue('#1006')
  def 'handle non-string message metadata values'() {
    given:
    def body = new PactDslJsonBody()
    Map<String, Object> metadata = [
      'contentType': 'application/json',
      'otherValue': 10L
    ]

    when:
    def pact = MessagePactBuilder
      .consumer('MessagePactBuilderSpec')
      .given('srm.countries.get_message')
      .expectsToReceive('srm.countries.get')
      .withMetadata(metadata)
      .withContent(body).toPact()
    Message message = pact.interactions.first()
    def messageMetadata = message.metaData

    then:
    messageMetadata == [contentType: 'application/json', otherValue: 10L]
  }

  def 'provider state can accept key/value pairs'() {
    given:
    def description = 'some state description'
    def params = ['stateKey': 'stateValue']
    def expectedProviderState = new ProviderState(description, params)

    when:
    def pact = MessagePactBuilder
      .consumer('MessagePactBuilderSpec')
      .given(description, params)

    then:
    pact.providerStates.last() == expectedProviderState
  }

  def 'provider state can accept ProviderState object'() {
    given:
    def description = 'some state description'
    def params = ['stateKey': 'stateValue']
    def expectedProviderState = new ProviderState(description, params)

    when:
    def pact = MessagePactBuilder
            .consumer('MessagePactBuilderSpec')
            .given(expectedProviderState)

    then:
    pact.providerStates.last() == expectedProviderState
  }

  def 'supports XML content'() {
    given:
    def xmlContent = new PactXmlBuilder("root")
    .build(root -> {
      root.appendElement("element1", Matchers.string("value1"))
    })
    Map<String, String> metadata = [
            'contentType': 'application/xml',
            'destination': Matchers.regexp(~/\w+\d+/, 'X001')
    ]
    def builder = MessagePactBuilder
            .consumer("MessagePactBuilderSpec")
            .given('srm.countries.get_message')
            .expectsToReceive('srm.countries.get')
            .withContent(xmlContent)
            .withMetadata(metadata)

    when:
    def pact = builder.toPact()
    Message message = pact.interactions.first()
    def messageBody = new XmlParser().parseText(message.contents.valueAsString())
    def messageMetadata = message.metaData

    then:
    messageBody.element1.text() == 'value1'
    messageMetadata == [contentType: 'application/xml', destination: 'X001']
    message.matchingRules.rules.body.matchingRules.keySet() == [ '$.root.element1.#text' ] as Set
    message.matchingRules.rules.metadata.matchingRules.keySet() == [
            'destination'
    ] as Set

  }

  @Issue('#1278')
  def 'Include any generators defined for the message contents'() {
    given:
    def body = new PactDslJsonBody()
      .datetime('DT')
    def category = au.com.dius.pact.core.model.generators.Category.BODY

    when:
    def pact = MessagePactBuilder
      .consumer('MessagePactBuilderSpec')
      .expectsToReceive('a message with generators')
      .withContent(body).toPact()
    Message message = pact.interactions.first()
    def generators = message.generators

    then:
    generators.categories[category] == ['$.DT': new DateTimeGenerator("yyyy-MM-dd'T'HH:mm:ss")]
  }

  @Issue('#1619')
  def 'support non-json text formats'() {
    when:
    def pact = new MessagePactBuilder()
      .consumer('MessagePactBuilderSpec')
      .expectsToReceive('a message with text contents')
      .withContent('a=b&c=d', 'application/x-www-form-urlencoded')
      .toPact()
    Message message = pact.interactions.first()

    then:
    message.contents.valueAsString() == 'a=b&c=d'
    message.contents.contentType.toString() == 'application/x-www-form-urlencoded'
  }
}
