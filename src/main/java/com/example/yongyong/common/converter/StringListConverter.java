package com.example.yongyong.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    throw new UnsupportedOperationException("yongin DB는 읽기 전용입니다.");
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    return dbData == null ? null : MAPPER.readValue(dbData, STRING_LIST);
  }
}
