package com.example.yongyong.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class OptionListConverter implements AttributeConverter<List<OptionJson>, String> {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TypeReference<List<OptionJson>> OPTION_LIST = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<OptionJson> attribute) {
    throw new UnsupportedOperationException("yongin DB는 읽기 전용입니다.");
  }

  @Override
  public List<OptionJson> convertToEntityAttribute(String dbData) {
    return dbData == null ? null : MAPPER.readValue(dbData, OPTION_LIST);
  }
}
