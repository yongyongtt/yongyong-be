package com.example.yongyong.yongin.entity;

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
    "select year, coalesce(month, 0) as month, unit, region_cd, type_nm, type_desc, type_chips,"
        + " low_reliability, reliability_note from yongin.ml_region")
@IdClass(MlRegion.Id.class)
@Getter
public class MlRegion {

  public record Id(Integer year, Integer month, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private String typeNm;
  private String typeDesc;

  @Convert(converter = StringListConverter.class)
  private List<String> typeChips;

  private Boolean lowReliability;
  private String reliabilityNote;
}
