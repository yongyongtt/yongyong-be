package com.example.yongyong.yongin.entity;

import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.common.converter.OptionListConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "ml_explanation")
@IdClass(MlExplanation.Id.class)
@Getter
public class MlExplanation {

  public record Id(Integer year, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private Boolean isKeep;

  @Convert(converter = OptionListConverter.class)
  private List<OptionJson> options;

  private String summary;
}
