package au.com.dius.pact.consumer;

import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue;
import au.com.dius.pact.core.support.json.JsonValue;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import static org.cthul.matchers.CthulMatchers.matchesPattern;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

public class PactDslJsonBodyTest {

  private static final String NUMBERS = "numbers";
  private static final String K_DEPRECIATION_BIPS = "10k-depreciation-bips";
  private static final String FIRST = "first";
  private static final String LEVEL_1 = "level1";
  private static final String L_1_EXAMPLE = "l1example";
  private static final String SECOND = "second";
  private static final String LEVEL_2 = "level2";
  private static final String L_2_EXAMPLE = "l2example";
  private static final String THIRD = "@third";

  @Test
  public void noSpecialHandlingForObjectNames() {
    DslPart body = new PactDslJsonBody()
      .id()
      .object("2")
      .id()
      .stringValue("test", "A Test String")
      .closeObject()
      .array(NUMBERS)
      .id()
      .number(100)
      .numberValue(101)
      .hexValue()
      .object()
      .id()
      .stringValue("name", "Rogger the Dogger")
      .timestamp()
      .date("dob", "MM/dd/yyyy")
      .object(K_DEPRECIATION_BIPS)
      .id()
      .closeObject()
      .closeObject()
      .closeArray();

    Set<String> expectedMatchers = new HashSet<String>(Arrays.asList(
      ".id",
      ".2.id",
      ".numbers[3]",
      ".numbers[0]",
      ".numbers[4].timestamp",
      ".numbers[4].dob",
      ".numbers[4].id",
      ".numbers[4].10k-depreciation-bips.id"
    ));
    assertThat(body.getMatchers().getMatchingRules().keySet(), is(equalTo(expectedMatchers)));

    assertThat(body.getBody().asObject().keys(), is(equalTo(new HashSet(Arrays.asList("2", NUMBERS, "id")))));
  }

  @Test
  public void matcherPathTest() {
    DslPart body = new PactDslJsonBody()
      .id("1")
      .stringType("@field")
      .hexValue("200", "abc")
      .integerType(K_DEPRECIATION_BIPS);

    Set<String> expectedMatchers = new HashSet<String>(Arrays.asList(
      ".200", ".1", ".@field", ".10k-depreciation-bips"
    ));
    assertThat(body.getMatchers().getMatchingRules().keySet(), is(equalTo(expectedMatchers)));

    assertThat(body.getBody().asObject().keys(),
      is(equalTo(new HashSet(Arrays.asList("200", K_DEPRECIATION_BIPS, "1", "@field")))));
  }

  @Test
  public void eachLikeMatcherTest() {
    DslPart body = new PactDslJsonBody()
      .eachLike("ids")
      .id()
      .closeObject()
      .closeArray();

    DslPart idsBody = new PactDslJsonBody().id();
    DslPart body2 = new PactDslJsonBody()
      .eachLike("ids", idsBody);Set<String> expectedMatchers = new HashSet<String>(Arrays.asList(
      ".ids",
      ".ids[*].id"
    ));
    assertThat(body.getMatchers().getMatchingRules().keySet(), is(equalTo(expectedMatchers)));assertThat(body2.getMatchers().getMatchingRules().keySet(), is(equalTo(expectedMatchers)));

    Set<String> ids = new HashSet<>(Collections.singletonList("ids"));
    assertThat(body.getBody().asObject().keys(), is(equalTo(ids)));
    assertThat(body2.getBody().asObject().keys(), is(equalTo(ids)));
    assertThat(body.getBody().toString(), is(equalTo(body2.getBody().toString())));
  }

  @Test
  public void nestedObjectMatcherTest() {
    DslPart body = new PactDslJsonBody()
      .object(FIRST)
      .stringType(LEVEL_1, L_1_EXAMPLE)
      .stringType("@level1")
      .object(SECOND)
      .stringType(LEVEL_2, L_2_EXAMPLE)
      .object(THIRD)
      .stringType("level3", "l3example")
      .object("fourth")
      .stringType("level4", "l4example")
      .closeObject()
      .closeObject()
      .closeObject()
      .closeObject();

    Set<String> expectedMatchers = new HashSet<>(Arrays.asList(
      ".first.second.@third.fourth.level4",
      ".first.second.@third.level3",
      ".first.second.level2",
      ".first.level1",
      ".first.@level1"
    ));

    assertThat(body.getMatchers().getMatchingRules().keySet(),
      is(equalTo(expectedMatchers)));
    assertThat(body.getBody().asObject().get(FIRST).get(LEVEL_1).asString(),
      is(equalTo(L_1_EXAMPLE)));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(LEVEL_2).asString(),
      is(equalTo(L_2_EXAMPLE)));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(THIRD).get("level3").asString(),
      is(equalTo("l3example")));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(THIRD).get("fourth").get("level4").asString(),
      is(equalTo("l4example")));
  }

  @Test
  public void nestedArrayMatcherTest() {
    DslPart body = new PactDslJsonBody()
      .array(FIRST)
      .stringType(L_1_EXAMPLE)
      .array()
      .stringType(L_2_EXAMPLE)
      .closeArray()
      .closeArray();

    Set<String> expectedMatchers = new HashSet<String>(Arrays.asList(
      ".first[0]",
      ".first[1][0]"
    ));

    assertThat(body.getMatchers().getMatchingRules().keySet(),
      is(equalTo(expectedMatchers)));
    assertThat(body.getBody().asObject().get(FIRST).get(0).asString(),
      is(equalTo(L_1_EXAMPLE)));
    assertThat(body.getBody().asObject().get(FIRST).get(1).get(0).asString(),
      is(equalTo(L_2_EXAMPLE)));
  }

  @Test
  public void nestedArrayAndObjectMatcherTest() {
    DslPart body = new PactDslJsonBody()
      .object(FIRST)
      .stringType(LEVEL_1, L_1_EXAMPLE)
      .array(SECOND)
      .stringType("al2example")
      .object()
      .stringType(LEVEL_2, L_2_EXAMPLE)
      .array("third")
      .stringType("al3example")
      .closeArray()
      .closeObject()
      .closeArray()
      .closeObject();

    Set<String> expectedMatchers = new HashSet<String>(Arrays.asList(
      ".first.level1",
      ".first.second[1].level2",
      ".first.second[0]",
      ".first.second[1].third[0]"
    ));

    assertThat(body.getMatchers().getMatchingRules().keySet(),
      is(equalTo(expectedMatchers)));
    assertThat(body.getBody().asObject().get(FIRST).get(LEVEL_1).asString(),
      is(equalTo(L_1_EXAMPLE)));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(0).asString(),
      is(equalTo("al2example")));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(1).get(LEVEL_2).asString(),
      is(equalTo(L_2_EXAMPLE)));
    assertThat(body.getBody().asObject().get(FIRST).get(SECOND).get(1).get("third").get(0).asString(),
      is(equalTo("al3example")));
  }

  @Test
  public void allowSettingFieldsToNull() {
    DslPart body = new PactDslJsonBody()
      .id()
      .object("2")
      .id()
      .stringValue("test", null)
      .nullValue("nullValue")
      .closeObject()
      .array(NUMBERS)
      .id()
      .nullValue()
      .stringValue(null)
      .closeArray();

    JsonValue.Object jsonObject = body.getBody().asObject();
    assertThat(jsonObject.keys(), is(equalTo(new HashSet(Arrays.asList("2", NUMBERS, "id")))));

    assertThat(jsonObject.get("2").get("test"), is(JsonValue.Null.INSTANCE));
    JsonValue.Array numbers = jsonObject.get(NUMBERS).asArray();
    assertThat(numbers.size(), is(3));
    assertThat(numbers.get(0), is(not(JsonValue.Null.INSTANCE)));
    assertThat(numbers.get(1), is(JsonValue.Null.INSTANCE));
    assertThat(numbers.get(2), is(JsonValue.Null.INSTANCE));
  }

  @Test
  public void testLargeDateFormat() {
    String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss +HHMM 'GMT'";
    final PactDslJsonBody response = new PactDslJsonBody();
    response
      .date("lastUpdate", DATE_FORMAT)
      .date("creationDate", DATE_FORMAT);
    JsonValue.Object jsonObject = response.getBody().asObject();
    assertThat(jsonObject.get("lastUpdate").toString(), matchesPattern("\\w{2,3}\\.?, \\d{2} \\w{3}\\.? \\d{4} \\d{2}:00:00 \\+\\d+ GMT"));
  }

  @Test
  public void testExampleTimestampTimezone() {
    final PactDslJsonBody response = new PactDslJsonBody();
    response
      .datetime("timestampLosAngeles", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", new Date(0), TimeZone.getTimeZone("America/Los_Angeles"))
      .datetime("timestampBerlin", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", new Date(0), TimeZone.getTimeZone("Europe/Berlin"))
      .date("dateLosAngeles", "yyyy-MM-dd", new Date(0), TimeZone.getTimeZone("America/Los_Angeles"))
      .date("dateBerlin", "yyyy-MM-dd", new Date(0), TimeZone.getTimeZone("Europe/Berlin"))
      .time("timeLosAngeles", "HH:mm:ss", new Date(0), TimeZone.getTimeZone("America/Los_Angeles"))
      .time("timeBerlin", "HH:mm:ss", new Date(0), TimeZone.getTimeZone("Europe/Berlin"));
    JsonValue.Object jsonObject = response.getBody().asObject();
    assertThat(jsonObject.get("timestampLosAngeles").toString(), is(equalTo("1969-12-31T16:00:00.000Z")));
    assertThat(jsonObject.get("timestampBerlin").toString(), is(equalTo("1970-01-01T01:00:00.000Z")));
    assertThat(jsonObject.get("dateLosAngeles").toString(), is(equalTo("1969-12-31")));
    assertThat(jsonObject.get("dateBerlin").toString(), is(equalTo("1970-01-01")));
    assertThat(jsonObject.get("timeLosAngeles").toString(), is(equalTo("16:00:00")));
    assertThat(jsonObject.get("timeBerlin").toString(), is(equalTo("01:00:00")));
  }

  @Test
  public void largeBodyTest() {
    PactDslJsonBody metadata = new PactDslJsonBody()
      .stringType("origin", "product-data")
      .datetimeExpression("dateCreated", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    PactDslJsonBody title = new PactDslJsonBody()
      .stringType("mainTitle", "Lorem ipsum dolor sit amet, consectetur adipiscing elit")
      .stringType("webTitle", "sample_data")
      .minArrayLike("attributes", 1)
      .stringType("key", "sample_data")
      .stringType("value", "sample_data")
      .closeObject()
      .closeArray().asBody();
    PactDslJsonBody description = new PactDslJsonBody()
      .stringType("longDescription", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Tellus pellentesque eu tincidunt tortor aliquam nulla facilisi cras. Nunc sed id semper risus in. Sit amet consectetur adipiscing elit pellentesque. Gravida neque convallis a cras. Auctor augue mauris augue neque gravida. Lectus quam id leo in vitae turpis massa sed elementum. Quisque sagittis purus sit amet volutpat consequat. Interdum velit euismod in pellentesque massa. Eu scelerisque felis imperdiet proin fermentum leo. Vel orci porta non pulvinar neque laoreet suspendisse. Netus et malesuada fames ac turpis egestas maecenas pharetra convallis. Sagittis aliquam malesuada bibendum arcu vitae. Risus in hendrerit gravida rutrum. Varius duis at consectetur lorem donec massa sapien. Platea dictumst quisque sagittis purus sit amet volutpat. Dui sapien eget mi proin sed libero enim. Tincidunt praesent semper feugiat nibh sed pulvinar. Sollicitudin tempor id eu nisl nunc mi. Hac habitasse platea dictumst vestibulum rhoncus.")
      .stringType("shortDescription", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Tellus pellentesque eu tincidunt tortor aliquam nulla facilisi cras. Nunc sed id semper risus in. Sit amet consectetur adipiscing elit pellentesque. Gravida neque convallis a cras. Auctor augue mauris augue neque gravida. Lectus quam id leo in vitae turpis massa sed elementum. Quisque sagittis purus sit amet volutpat consequat. Interdum velit euismod in pellentesque massa. Eu scelerisque felis imperdiet proin fermentum leo. Vel orci porta non pulvinar neque laoreet suspendisse. Netus et malesuada fames ac turpis egestas maecenas pharetra convallis. Sagittis aliquam malesuada bibendum arcu vitae. Risus in hendrerit gravida rutrum. Varius duis at consectetur lorem donec massa sapien. Platea dictumst quisque sagittis purus sit amet volutpat. Dui sapien eget mi proin sed libero enim.")
      .minArrayLike("attributes", 1)
      .stringType("key", "sample_data")
      .stringType("value", "sample_data")
      .closeObject()
      .closeArray().asBody();
    PactDslJsonBody productSpecification = new PactDslJsonBody().integerType("multiPackQuantity", 1)
      .booleanType("copyrightInd", false)
      .stringType("copyrightDets", "sample_data")
      .booleanType("batteryRequired", true)
      .booleanType("batteryIncluded", false)
      .booleanType("beabApproved", false)
      .stringType("beabCertNo", "sample_data")
      .booleanType("plugRequired", false)
      .booleanType("plugIncluded", false)
      .booleanType("bulbRequired", false)
      .booleanType("bulbIncluded", false)
      .decimalType("voltage", 10.10)
      .decimalType("wattage", 10.10);
    PactDslJsonBody dimensions = new PactDslJsonBody()
      .decimalType("length", 10.10)
      .decimalType("width", 10.10)
      .decimalType("height", 10.10)
      .decimalType("pileHeight", 10.10)
      .stringType("uom", "METRE");
    PactDslJsonBody brand = new PactDslJsonBody()
      .stringType("name", "sample_data");
    PactDslJsonBody eventHistories = new PactDslJsonBody()
      .stringType("eventService", "fam-service")
      .datetimeExpression("eventCreationDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .stringType("eventType", "UPDATE")
      .datetimeExpression("eventProcessedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    PactDslJsonBody colours = new PactDslJsonBody()
      .stringType("name", "sample_data")
      .booleanType("primary", false)
      .stringType("colourCode", "sample_data")
      .stringType("hexCode", "sample_data")
      .stringType("rgbCode", "sample_data");
    PactDslJsonBody images = new PactDslJsonBody().stringType("name", "sample_data")
      .stringType("linkType", "URI")
      .stringType("uri", "153-7908284-BOK434X.jpg")
      .stringType("description", "sample_data")
      .stringType("type", "sample_data")
      .stringType("status", "sample_data")
      .minArrayLike("attributes", 1)
      .stringType("key", "channelFormat")
      .stringType("value", "X")
      .closeObject()
      .closeArray().asBody();
    PactDslJsonBody caseDimensions = new PactDslJsonBody()
      .decimalType("length", 10.10)
      .decimalType("width", 10.10)
      .decimalType("height", 10.10)
      .decimalType("weight", 10.10)
      .stringType("lwhUom", "MILLIMETRE")
      .stringType("weightUom", "GRAM");

    DslPart body = new PactDslJsonBody()
      .object("metadata", metadata)
      .integerType("version", 1)
      .object("wrapper")
      .stringType("w", "17f78aqr")
      .minArrayLike("identifiers", 1)
      .stringType("alias", "sku")
      .minArrayLike("value", 1, PactDslJsonRootValue.stringType("7908284"), 1)
      .closeArray().asBody()
      .minArrayLike("aliases", 1, PactDslJsonRootValue.stringType("17f78aqr"), 1)
      .closeObject().asBody()
      .stringType("itemType", "ITEM")
      .object("parentWrapper")
      .stringType("p", "xf7kabqd")
      .minArrayLike("identifiers", 1)
      .stringType("alias", "sku")
      .minArrayLike("value", 1, PactDslJsonRootValue.stringType("135325620.P"), 1)
      .closeArray().asBody()
      .minArrayLike("aliases", 1, PactDslJsonRootValue.stringType("xf7kabqd"), 1)
      .closeObject().asBody()
      .object("master")
      .stringType("source", "PDS")
      .object("title", title)
      .object("description", description)
      .object("brand", brand)
      .object("productSpecification", productSpecification)
      .minArrayLike("attributes", 1)
      .stringType("scope", "DESCRIPTIVE")
      .stringType("key", "sample_data")
      .minArrayLike("values", 1, PactDslJsonRootValue.stringType("sample_data"), 1)
      .closeObject()
      .closeArray().asBody()
      .minArrayLike("colours", 1, colours)
      .object("weightsAndMeasures")
      .object("dimensions", dimensions)
      .object("weight")
      .decimalType("weight", 10.10)
      .decimalType("netWeight", 10.10)
      .decimalType("catchWeight", 10.10)
      .decimalType("pileWeight", 10.10)
      .stringType("uom", "KILOGRAM")
      .closeObject().asBody()
      .object("volume")
      .decimalType("liquidVolume", 10.10)
      .stringType("uom", "LITRE")
      .closeObject().asBody()
      .object("scannedData")
      .datetimeExpression("fileTimestamp", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .decimalType("averageWeight", 10.10)
      .stringType("averageWeightUom", "GRAM")
      .object("shipmentCase", caseDimensions)
      .object("vendorCase", caseDimensions)
      .closeObject().asBody()
      .closeObject().asBody()
      .minArrayLike("packages", 1)
      .integerType("packageType", 1)
      .object("weightsAndMeasures")
      .object("dimensions", dimensions)
      .object("weight")
      .decimalType("weight", 10.10)
      .decimalType("netWeight", 10.10)
      .decimalType("catchWeight", 10.10)
      .decimalType("pileWeight", 10.10)
      .stringType("uom", "KILOGRAM")
      .closeObject().asBody()
      .object("volume")
      .decimalType("liquidVolume", 10.10)
      .stringType("uom", "LITRE")
      .closeObject().asBody()
      .object("scannedData")
      .datetimeExpression("fileTimestamp", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .decimalType("averageWeight", 10.10)
      .stringType("averageWeightUom", "GRAM")
      .object("shipmentCase", caseDimensions)
      .object("vendorCase", caseDimensions)
      .closeObject().asBody()
      .closeObject().asBody()
      .closeObject()
      .closeArray().asBody()
      .object("media")
      .minArrayLike("images", 1, images)
      .closeObject().asBody()
      .object("safety")
      .booleanType("ageRestrictedFlag", false)
      .booleanType("safetyIndicator", false)
      .booleanType("safetyIndicatorFlag", false)
      .booleanType("safetyIndicatorOverrideFlag", false)
      .closeObject().asBody()
      .object("waste")
      .stringType("wasteType", "sample_data")
      .decimalType("percentage", 10.10)
      .decimalType("defaultPercentage", 10.10)
      .datetimeExpression("effectiveFromDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .datetimeExpression("effectiveToDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .minArrayLike("attributes", 1)
      .stringType("key", "associatedProductNumber")
      .stringType("value", "sample_data")
      .closeObject()
      .closeArray().asBody()
      .closeObject().asBody()
      .object("optionalTypes")
      .object("jewellery")
      .decimalType("totalWeight", 10.10)
      .decimalType("metalWeight", 10.10)
      .decimalType("stoneWeight", 10.10)
      .decimalType("chainLength", 10.10)
      .stringType("ringSize", "sample_data")
      .stringType("ringSizeFrom", "sample_data")
      .stringType("ringSizeTo", "sample_data")
      .closeObject().asBody()
      .object("clothing")
      .stringType("size", "sample_data")
      .closeObject().asBody()
      .eachLike("batteries", 0)
      .closeArray().asBody()
      .closeObject().asBody()
      .object("productDataAudit")
      .datetimeExpression("createdDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .datetimeExpression("lastModifiedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .stringType("lastModifiedBy", "argos-pim-backfeed-adapter-service")
      .datetimeExpression("deletedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .closeObject().asBody()
      .closeObject().asBody()
      .object("division")
      .stringType("masterSource", "DIV")
      .object("title", title)
      .object("description", description)
      .object("brand", brand)
      .object("productSpecification", productSpecification)
      .minArrayLike("attributes", 1)
      .stringType("scope", "DESCRIPTIVE")
      .stringType("key", "sample_data")
      .minArrayLike("values", 1, PactDslJsonRootValue.stringType("sample_data"), 1)
      .closeObject()
      .closeArray().asBody()
      .minArrayLike("colours", 1, colours)
      .object("weightsAndMeasures")
      .object("dimensions", dimensions)
      .object("weight")
      .decimalType("weight", 10.10)
      .decimalType("netWeight", 10.10)
      .decimalType("catchWeight", 10.10)
      .decimalType("pileWeight", 10.10)
      .stringType("uom", "KILOGRAM")
      .closeObject().asBody()
      .object("volume")
      .decimalType("liquidVolume", 10.10)
      .stringType("uom", "LITRE")
      .closeObject().asBody()
      .object("scannedData")
      .datetimeExpression("fileTimestamp", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .decimalType("averageWeight", 10.10)
      .stringType("averageWeightUom", "GRAM")
      .object("shipmentCase", caseDimensions)
      .object("vendorCase", caseDimensions)
      .closeObject().asBody()
      .closeObject().asBody()
      .minArrayLike("packages", 1)
      .integerType("packageType", 1)
      .object("weightsAndMeasures")
      .object("dimensions", dimensions)
      .object("weight")
      .decimalType("weight", 10.10)
      .decimalType("netWeight", 10.10)
      .decimalType("catchWeight", 10.10)
      .decimalType("pileWeight", 10.10)
      .stringType("uom", "KILOGRAM")
      .closeObject().asBody()
      .object("volume")
      .decimalType("liquidVolume", 10.10)
      .stringType("uom", "LITRE")
      .closeObject().asBody()
      .object("scannedData")
      .datetimeExpression("fileTimestamp", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .decimalType("averageWeight", 10.10)
      .stringType("averageWeightUom", "GRAM")
      .object("shipmentCase", caseDimensions)
      .object("vendorCase", caseDimensions)
      .closeObject().asBody()
      .closeObject().asBody()
      .closeObject()
      .closeArray().asBody()
      .object("media")
      .minArrayLike("images", 1, images)
      .closeObject().asBody()
      .object("safety")
      .booleanType("ageRestrictedFlag", false)
      .booleanType("safetyIndicator", false)
      .booleanType("safetyIndicatorFlag", false)
      .booleanType("safetyIndicatorOverrideFlag", false)
      .closeObject().asBody()
      .object("waste")
      .stringType("wasteType", "sample_data")
      .decimalType("percentage", 10.10)
      .decimalType("defaultPercentage", 10.10)
      .datetimeExpression("effectiveFromDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .datetimeExpression("effectiveToDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .minArrayLike("attributes", 1)
      .stringType("key", "weeeAssociatedProductNumber")
      .stringType("value", "sample_data")
      .closeObject()
      .closeArray().asBody()
      .closeObject().asBody()
      .object("optionalTypes")
      .object("jewellery")
      .decimalType("totalWeight", 10.10)
      .decimalType("metalWeight", 10.10)
      .decimalType("stoneWeight", 10.10)
      .decimalType("chainLength", 10.10)
      .stringType("ringSize", "sample_data")
      .stringType("ringSizeFrom", "sample_data")
      .stringType("ringSizeTo", "sample_data")
      .closeObject().asBody()
      .object("clothing")
      .stringType("size", "sample_data")
      .closeObject().asBody()
      .eachLike("batteries", 0)
      .closeArray().asBody()
      .closeObject().asBody()
      .object("productDataAudit")
      .datetimeExpression("createdDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .datetimeExpression("lastModifiedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .stringType("lastModifiedBy", "bam")
      .datetimeExpression("deletedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .closeObject().asBody()
      .closeObject().asBody()
      .object("exxer")
      .stringType("masterSource", "EXXER")
      .closeObject().asBody()
      .object("itemAudit")
      .datetimeExpression("createdDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .datetimeExpression("lastModifiedDate", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .stringType("lastModifiedBy", "bam")
      .minArrayLike("eventHistories", 1, eventHistories)
      .closeObject().asBody();

    JsonValue jsonValue = body.getBody();
    assertThat(jsonValue.asObject().getEntries().keySet(),
      is(equalTo(new HashSet<>(Arrays.asList("division", "metadata", "itemType", "itemAudit", "exxer", "wrapper",
        "parentWrapper", "version", "master")))));
    assertThat(jsonValue.asObject().get("itemAudit").asObject().getEntries().keySet(),
      is(equalTo(new HashSet<>(Arrays.asList("createdDate", "eventHistories", "lastModifiedBy", "lastModifiedDate")))));
    assertThat(jsonValue.asObject().get("itemAudit").get("eventHistories").asArray().getValues()
        .get(0).asObject().getEntries().keySet(),
      is(equalTo(new HashSet<>(Arrays.asList("eventProcessedDate", "eventType", "eventService", "eventCreationDate")))));
  }
}
