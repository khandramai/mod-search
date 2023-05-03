package org.folio.search.service.setter.item;

import static org.apache.commons.collections4.SetUtils.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.DEWEY_ID;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.LC_ID;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.LOCAL_CALL_NUMBER_TYPES_SOURCES;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.NLM_ID;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.OTHER_SCHEME_ID;
import static org.folio.search.service.setter.item.ItemTypedCallNumberProcessor.SUDOC_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.integration.ReferenceDataService;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ItemTypedCallNumberProcessorTest {

  private @Mock ReferenceDataService referenceDataService;

  private @InjectMocks ItemTypedCallNumberProcessor processor;

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder, String callNumberTypeId) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder).itemLevelCallNumberTypeId(callNumberTypeId);
  }

  public static Stream<Arguments> emptyCallNumberAfterProcessingSource() {
    return Stream.of(
      Arguments.arguments(null, null),
      Arguments.arguments("", ""),
      Arguments.arguments("", LC_ID),
      Arguments.arguments("AAA", ""),
      Arguments.arguments(null, LC_ID),
      Arguments.arguments("AAA", null),
      Arguments.arguments("()[]", LC_ID)
    );
  }

  @Test
  void getFieldValue_multipleValue_positive() {
    var localCnId = UUID.randomUUID().toString();

    when(referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, LOCAL_CALL_NUMBER_TYPES_SOURCES))
      .thenReturn(Set.of(localCnId));
    var eventBody = instance(
      item("HD 11", localCnId),
      item("HD 11", LC_ID),
      item("HD 11", DEWEY_ID),
      item("HD 11", NLM_ID),
      item("HD 11", SUDOC_ID),
      item("HD 11", OTHER_SCHEME_ID)
    );
    var actual = processor.getFieldValue(eventBody);
    assertThat(actual).containsExactly(84787310808212480L,
      229342416757269504L,
      373897522706326528L,
      518452628655383552L,
      663007734604440576L,
      807562840553497600L);
  }

  @ParameterizedTest
  @MethodSource("emptyCallNumberAfterProcessingSource")
  void getFieldValue_emptyCallNumberAfterProcessing(String effectiveShelvingOrder, String callNumberTypeId) {
    var eventBody = instance(item(effectiveShelvingOrder, callNumberTypeId));
    var actual = processor.getFieldValue(eventBody);
    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_emptyCallNumberIfNotSystemOrNotLocal() {
    when(referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, LOCAL_CALL_NUMBER_TYPES_SOURCES))
      .thenReturn(emptySet());
    var eventBody = instance(item("AA 123", UUID.randomUUID().toString()));
    var actual = processor.getFieldValue(eventBody);
    assertThat(actual).isEmpty();
  }
}
