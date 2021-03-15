package org.folio.search.controller;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ItemStatus.NameEnum.AVAILABLE;
import static org.folio.search.domain.dto.ItemStatus.NameEnum.CHECKED_OUT;
import static org.folio.search.domain.dto.ItemStatus.NameEnum.MISSING;
import static org.folio.search.support.base.ApiEndpoints.getFacets;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.facetResult;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceTags;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemStatus;
import org.folio.search.domain.dto.ItemStatus.NameEnum;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SearchInstanceFilterIT extends BaseIntegrationTest {

  private static final String TENANT_ID = "filter_test_instance";

  private static final String[] IDS = array(
    "1353873c-0e5e-4d64-a2f9-6c444dc4cd46",
    "cc6bbc19-3f54-43c5-8736-b85688619641",
    "39a52d91-8dbb-4348-ab06-5c6115e600cd",
    "62f72eeb-ed5a-4619-b01f-1750d5528d25",
    "6d9ccc82-8142-4fbc-b6ba-8e3429fd9aca");

  private static final String[] FORMATS = array(
    "89f6d4f0-9cd2-4015-828d-331dc3adb47a",
    "25a81102-a2a9-4576-85ff-133ebcbcef2c",
    "e57e36a3-80ff-46a6-ac2f-5c8bd79bc2bb");

  private static final String[] TYPES = array(
    "24da24dd-03ae-4e34-bad6-c79e342baeb9",
    "de9e38bb-89a8-43ee-922f-c973b122cbb3");

  private static final String[] LOCATIONS = array(
    "ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2",
    "f1a49577-5096-4771-a8a0-d07d642241eb");

  private static final String[] PERMANENT_LOCATIONS = array(
    "765b4c3b-485c-4ce4-a117-f99c01ac49fe",
    "4fdca025-1629-4688-aeb7-9c5fe5c73549",
    "81f1ab2c-83c5-4a90-a8b7-c8c8179c0697");

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, instances());
  }

  @AfterAll
  static void removeTenant(@Autowired RestHighLevelClient client, @Autowired JdbcTemplate template) {
    removeTenant(client, template, TENANT_ID);
  }

  @MethodSource("filteredSearchQueriesProvider")
  @DisplayName("searchByInstances_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByInstances_parameterized(String query, List<String> expectedIds) throws Exception {
    mockMvc.perform(get(searchInstancesByQuery(query)).headers(defaultHeaders(TENANT_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("instances[*].id", is(expectedIds)));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForInstances_parameterized")
  void getFacetsForInstances_parameterized(String query, String[] facets, Map<String, Facet> expected)
    throws Throwable {
    var jsonString = mockMvc.perform(get(getFacets(query, facets)).headers(defaultHeaders(TENANT_ID)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var actual = OBJECT_MAPPER.readValue(jsonString, FacetResult.class);
    assertThat(actual).isEqualTo(facetResult(expected));
  }

  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
      arguments("(id=*) sortby title", List.of(IDS)),
      arguments("(id=* and source==\"MARC\") sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(id=* and source==\"FOLIO\") sortby title", List.of(IDS[2], IDS[4])),

      arguments("(id=* and languages==\"eng\") sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(id=* and languages==\"ger\") sortby title", List.of(IDS[1])),
      arguments("(id=* and languages==\"ita\") sortby title", List.of(IDS[0], IDS[3])),
      arguments("(id=* and languages==\"fra\") sortby title", List.of(IDS[1], IDS[4])),
      arguments("(id=* and languages==\"rus\") sortby title", List.of(IDS[2])),
      arguments("(id=* and languages==\"ukr\") sortby title", List.of(IDS[2])),
      arguments("(languages==\"ukr\") sortby title", List.of(IDS[2])),

      arguments("(id=* and source==\"MARC\" and languages==\"eng\") sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and source==\"FOLIO\" and languages==\"eng\") sortby title", List.of(IDS[4])),
      arguments("(source==\"FOLIO\" and languages==\"eng\") sortby title", List.of(IDS[4])),

      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[0]), List.of(IDS[1], IDS[2])),
      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[1]), List.of(IDS[0], IDS[3], IDS[4])),

      arguments(format("(id=* and instanceFormatId==\"%s\") sortby title", FORMATS[0]), List.of(IDS[3])),
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[1]),
        List.of(IDS[0], IDS[1], IDS[3], IDS[4])),
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[2]), List.of(IDS[0], IDS[2], IDS[3])),
      arguments(format("(id=* and instanceFormatId==(%s or %s)) sortby title", FORMATS[1], FORMATS[2]), List.of(IDS)),

      arguments("(id=* and staffSuppress==true) sortby title", List.of(IDS[0], IDS[1], IDS[2])),
      arguments("(id=* and staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),
      arguments("(staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),

      arguments("(id=* and discoverySuppress==true) sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(id=* and staffSuppress==true and discoverySuppress==false) sortby title", List.of(IDS[2])),

      arguments("(id=* and instanceTags==text) sortby title", List.of(IDS[0])),
      arguments("(id=* and instanceTags==science) sortby title", List.of(IDS[0], IDS[2])),
      arguments("(instanceTags==science) sortby title", List.of(IDS[0], IDS[2])),

      arguments(format("(id=* and items.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),
      arguments(format("(id=* and items.effectiveLocationId==%s) sortby title", LOCATIONS[1]),
        List.of(IDS[1], IDS[2], IDS[4])),
      arguments(format("(items.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),

      arguments("(items.status.name==Available) sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(items.status.name==Missing) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(items.status.name==\"Checked out\") sortby title", List.of(IDS[2], IDS[4])),
      arguments("(items.status.name==Available and source==MARC) sortby title", List.of(IDS[0], IDS[1])),

      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[0]),
        List.of(IDS[0], IDS[3])),
      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[1]),
        List.of(IDS[1], IDS[3])),
      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[2]),
        List.of(IDS[3], IDS[4])),
      arguments(format("(holdings.permanentLocationId==%s and source==MARC) sortby title", PERMANENT_LOCATIONS[2]),
        List.of(IDS[3]))
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array("discoverySuppress", "staffSuppress", "languages", "instanceTags", "source",
      "instanceTypeId", "instanceFormatId", "items.effectiveLocationId", "items.status.name",
      "holdings.permanentLocationId");
    return Stream.of(
      arguments("id=*", allFacets, mapOf(
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)),
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)),
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2), facetItem("ita", 2),
          facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)),
        "instanceTags", facet(facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
          facetItem("casual", 1), facetItem("text", 1)),
        "source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)),
        "instanceTypeId", facet(facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)),
        "instanceFormatId", facet(facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)),
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 4), facetItem(LOCATIONS[1], 3)),
        "items.status.name", facet(facetItem("Available", 3), facetItem("Checked out", 2), facetItem("Missing", 2)),
        "holdings.permanentLocationId", facet(facetItem(PERMANENT_LOCATIONS[1], 2),
          facetItem(PERMANENT_LOCATIONS[0], 2), facetItem(PERMANENT_LOCATIONS[2], 2)))),

      arguments("id=*", array("source"), mapOf("source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)))),

      arguments("id=*", array("languages"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2),
        facetItem("ita", 2), facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)))),

      arguments("id=*", array("languages:2"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("languages==eng", array("languages:2"), mapOf(
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("id=*", array("discoverySuppress"), mapOf(
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)))),

      arguments("id=*", array("staffSuppress"), mapOf(
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)))),

      arguments("id=*", array("instanceTags"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
        facetItem("casual", 1), facetItem("text", 1)))),

      arguments("id=*", array("instanceTags:3"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2)))),

      arguments("id=*", array("instanceTypeId"), mapOf("instanceTypeId", facet(
        facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)))),

      arguments("id=*", array("instanceFormatId"), mapOf("instanceFormatId", facet(
        facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)))),

      arguments("instanceFormatId==" + FORMATS[0], array("instanceFormatId"), mapOf(
        "instanceFormatId", facet(facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)))),

      arguments("source==MARC", array("instanceFormatId"), mapOf(
        "instanceFormatId", facet(facetItem(FORMATS[1], 3), facetItem(FORMATS[2], 2), facetItem(FORMATS[0], 1)))),

      arguments("id=*", array("items.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 4), facetItem(LOCATIONS[1], 3)))),

      arguments("source==MARC", array("source", "items.effectiveLocationId"), mapOf(
        "source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)),
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 2), facetItem(LOCATIONS[1], 1)))),

      arguments("id=*", array("items.status.name"), mapOf(
        "items.status.name", facet(facetItem("Available", 3), facetItem("Checked out", 2), facetItem("Missing", 2)))),

      arguments("id=*", array("holdings.permanentLocationId"), mapOf(
        "holdings.permanentLocationId", facet(facetItem(PERMANENT_LOCATIONS[1], 2),
          facetItem(PERMANENT_LOCATIONS[0], 2), facetItem(PERMANENT_LOCATIONS[2], 2))))
    );
  }

  private static Instance[] instances() {
    var instances = IntStream.range(0, 5)
      .mapToObj(i -> new Instance().id(IDS[i]).title("Resource" + i))
      .toArray(Instance[]::new);

    instances[0]
      .source("MARC")
      .languages(List.of("eng", "ita"))
      .instanceTypeId(TYPES[1])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatId(List.of(FORMATS[1], FORMATS[2]))
      .tags(instanceTags("text", "science"))
      .items(List.of(new Item().id(randomId()).effectiveLocationId(LOCATIONS[0]).status(itemStatus(AVAILABLE))))
      .holdings(List.of(new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[0])));

    instances[1]
      .source("MARC")
      .languages(List.of("eng", "ger", "fra"))
      .instanceTypeId(TYPES[0])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatId(List.of(FORMATS[1]))
      .tags(instanceTags("future"))
      .items(List.of(new Item().id(randomId()).effectiveLocationId(LOCATIONS[1]).status(itemStatus(AVAILABLE))))
      .holdings(List.of(new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[1])));

    instances[2]
      .source("FOLIO")
      .languages(List.of("rus", "ukr"))
      .instanceTypeId(TYPES[0])
      .staffSuppress(true)
      .instanceFormatId(List.of(FORMATS[2]))
      .tags(instanceTags("future", "science"))
      .items(List.of(
        new Item().id(randomId()).effectiveLocationId(LOCATIONS[0]).status(itemStatus(MISSING)),
        new Item().id(randomId()).effectiveLocationId(LOCATIONS[1]).status(itemStatus(CHECKED_OUT))));

    instances[3]
      .source("MARC")
      .languages(List.of("ita"))
      .staffSuppress(false)
      .discoverySuppress(false)
      .instanceTypeId(TYPES[1])
      .instanceFormatId(List.of(FORMATS))
      .tags(instanceTags("casual", "cooking"))
      .items(List.of(new Item().id(randomId()).effectiveLocationId(LOCATIONS[0]).status(itemStatus(MISSING))))
      .holdings(List.of(
        new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[0]),
        new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[1]),
        new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[2])));

    instances[4]
      .source("FOLIO")
      .languages(List.of("eng", "fra"))
      .instanceTypeId(TYPES[1])
      .instanceFormatId(List.of(FORMATS[1]))
      .tags(instanceTags("cooking"))
      .items(List.of(
        new Item().id(randomId()).effectiveLocationId(LOCATIONS[0]).status(itemStatus(CHECKED_OUT)),
        new Item().id(randomId()).effectiveLocationId(LOCATIONS[1]).status(itemStatus(AVAILABLE))))
      .holdings(List.of(new Holding().id(randomId()).permanentLocationId(PERMANENT_LOCATIONS[2])));

    return instances;
  }

  private static ItemStatus itemStatus(NameEnum itemStatus) {
    return new ItemStatus().name(itemStatus);
  }

  private static InstanceTags instanceTags(String... tags) {
    return new InstanceTags().tagList(asList(tags));
  }
}
