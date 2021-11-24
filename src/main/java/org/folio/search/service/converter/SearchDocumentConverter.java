package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final JsonConverter jsonConverter;
  private final SearchFieldsProcessor searchFieldsProcessor;
  private final LanguageConfigService languageConfigService;
  private final ResourceDescriptionService descriptionService;

  /**
   * Converts {@link ResourceEvent} object to the {@link SearchDocumentBody} objects.
   *
   * @param resourceEvent - resource event for conversion to Elasticsearch document
   * @return list with elasticsearch documents.
   */
  public Optional<SearchDocumentBody> convert(ResourceEvent resourceEvent) {
    return canConvertEvent(resourceEvent)
      ? Optional.of(convert(buildConversionContext(resourceEvent)))
      : Optional.empty();
  }

  private SearchDocumentBody convert(ConversionContext context) {
    var resourceData = context.getResourceData();
    var resourceDescriptionFields = context.getResourceDescription().getFields();
    var baseFields = convertMapUsingResourceFields(resourceData, resourceDescriptionFields, context);
    var searchFields = searchFieldsProcessor.getSearchFields(context);
    var resultDocument = mergeSafely(baseFields, searchFields);
    return SearchDocumentBody.forConversionContext(context, jsonConverter.toJson(resultDocument));
  }

  private List<String> getResourceLanguages(List<String> languageSource, Map<String, Object> resourceData) {
    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    return languageSource.stream()
      .map(sourcePath -> SearchConverterUtils.getMapValueByPath(sourcePath, resourceData))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(supportedLanguages::contains)
      .collect(toList());
  }

  private static boolean canConvertEvent(ResourceEvent resourceEvent) {
    return resourceEvent.getNew() instanceof Map;
  }

  private ConversionContext buildConversionContext(ResourceEvent event) {
    var resourceDescription = descriptionService.get(event.getResourceName());
    var resourceData = getEventPayload(event);
    var resourceLanguages = getResourceLanguages(resourceDescription.getLanguageSourcePaths(), resourceData);
    return ConversionContext.of(event.getId(), event.getTenant(), resourceData, resourceDescription, resourceLanguages);
  }

  private static Map<String, Object> convertMapUsingResourceFields(
    Map<String, Object> data, Map<String, FieldDescription> fields, ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();
    fields.entrySet().forEach(entry -> resultMap.putAll(getFieldValue(data, entry, ctx)));
    return nullIfEmpty(resultMap);
  }

  private static Map<String, Object> getFieldValue(
    Map<String, Object> data, Entry<String, FieldDescription> descEntry, ConversionContext ctx) {
    var fieldDescription = descEntry.getValue();
    if (fieldDescription instanceof PlainFieldDescription) {
      return getPlainFieldValue(data, descEntry, ctx);
    }

    var objectFieldDescription = (ObjectFieldDescription) fieldDescription;
    var fieldName = descEntry.getKey();
    var objectMapValue = data.get(fieldName);
    var value = getObjectFieldValue(objectMapValue, objectFieldDescription.getProperties(), ctx);
    return value != null ? Map.of(fieldName, value) : emptyMap();
  }

  private static Map<String, Object> getPlainFieldValue(Map<String, Object> fieldData,
    Entry<String, FieldDescription> fieldEntry, ConversionContext ctx) {
    var fieldName = fieldEntry.getKey();
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (desc.isNotIndexed()) {
      return emptyMap();
    }

    var plainFieldValue = MapUtils.getObject(fieldData, fieldName, desc.getDefaultValue());
    if (plainFieldValue == null) {
      return emptyMap();
    }

    return SearchUtils.getPlainFieldValue(desc, fieldName, plainFieldValue, ctx.getLanguages());
  }

  @SuppressWarnings("unchecked")
  private static Object getObjectFieldValue(
    Object value, Map<String, FieldDescription> subfields, ConversionContext ctx) {
    if (value instanceof Map) {
      return convertMapUsingResourceFields((Map<String, Object>) value, subfields, ctx);
    }

    if (value instanceof List) {
      return ((List<Object>) value).stream()
        .map(listValue -> getObjectFieldValue(listValue, subfields, ctx))
        .filter(Objects::nonNull)
        .collect(toList());
    }

    return null;
  }
}
