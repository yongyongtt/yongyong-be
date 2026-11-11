package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// 연간(month NULL) 행은 month 0으로 읽는다. 복합 키에 NULL이 있으면 Hibernate가 null 엔티티를 돌려준다 (decisions D16)
@Entity
@Immutable
@Subselect(
    "select year, coalesce(month, 0) as month, unit, region_cd, region_nm, gu, score_total"
        + " from yongin.ml_priority")
@IdClass(MlPriority.Id.class)
@Getter
public class MlPriority {

  public record Id(Integer year, Integer month, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private String regionNm;
  private String gu;
  private Double scoreTotal;
}
