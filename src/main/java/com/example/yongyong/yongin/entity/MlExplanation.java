package com.example.yongyong.yongin.entity;

import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.common.converter.OptionListConverter;
import com.example.yongyong.common.converter.StringListConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// 연간(month NULL) 행은 month 0으로 읽는다 (MlPriority와 같은 이유, decisions D16)
@Entity
@Immutable
@Subselect(
    "select year, coalesce(month, 0) as month, unit, region_cd, is_keep, options, reasons,"
        + " cautions, summary, created_at from yongin.ml_explanation")
@IdClass(MlExplanation.Id.class)
@Getter
public class MlExplanation {

  public record Id(Integer year, Integer month, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private Boolean isKeep;

  @Convert(converter = OptionListConverter.class)
  private List<OptionJson> options;

  @Convert(converter = StringListConverter.class)
  private List<String> reasons;

  @Convert(converter = StringListConverter.class)
  private List<String> cautions;

  private String summary;
  private String createdAt;
}
