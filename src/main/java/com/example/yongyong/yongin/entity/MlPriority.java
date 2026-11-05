package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

@Entity
@Immutable
@Subselect(
    """
    SELECT year, COALESCE(month, 0) AS month_key, unit, region_cd, region_nm, gu,
           score_total, rank_total, is_vulnerable
    FROM yongin.ml_priority
    """)
@Synchronize("ml_priority")
@IdClass(MlPriority.Id.class)
@Getter
public class MlPriority {

  public record Id(Integer year, Integer monthKey, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer monthKey;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private String regionNm;
  private String gu;
  private Double scoreTotal;
  private Integer rankTotal;
  private Boolean isVulnerable;
}
