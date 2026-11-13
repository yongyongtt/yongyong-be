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
    "select year, coalesce(month, 0) as month, plan, plan_nm, plan_desc, sort_basis, budget,"
        + " total_cost, budget_left, n_included, n_skipped, pop65_total, n_bus, n_drt, n_happy,"
        + " days"
        + " from yongin.plan_summary")
@IdClass(PlanSummary.Id.class)
@Getter
public class PlanSummary {

  public record Id(Integer year, Integer month, Integer plan) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private Integer plan;
  private String planNm;
  private String planDesc;
  private String sortBasis;
  private Long budget;
  private Long totalCost;
  private Long budgetLeft;
  private Integer nIncluded;
  private Integer nSkipped;
  private Integer pop65Total;
  private Integer nBus;
  private Integer nDrt;
  private Integer nHappy;
  private Integer days;
}
