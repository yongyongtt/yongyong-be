package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// 연간(month NULL) 행은 month 0으로 읽는다 (MlPriority와 같은 이유, decisions D16)
@Entity
@Immutable
@Subselect(
    "select year, coalesce(month, 0) as month, plan, unit, region_cd, region_nm, gu, status,"
        + " is_included, seq, plan_rank, mode, mode_nm, action, cost, budget_left_at_skip,"
        + " score_total"
        + " from yongin.plan_region")
@IdClass(PlanRegion.Id.class)
@Getter
public class PlanRegion {

  public record Id(Integer year, Integer month, Integer plan, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private Integer plan;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private String regionNm;
  private String gu;
  private String status;
  private Boolean isIncluded;
  private Integer seq;
  private Integer planRank;
  private String mode;
  private String modeNm;
  private String action;
  private Long cost;
  private Long budgetLeftAtSkip;
  private Double scoreTotal;
}
