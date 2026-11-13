package com.example.yongyong.plan.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record PlanResponse(PeriodResponse period, List<Plan> plans) {

  public record Plan(
      Integer plan,
      String name,
      String desc,
      String sortBasis,
      Long budget,
      Integer days,
      Long totalCost,
      Long budgetLeft,
      Integer includedCount,
      Integer skippedCount,
      Integer pop65Total,
      List<ModeCount> modeCounts,
      List<Region> regions,
      List<Skipped> skipped) {}

  public record ModeCount(String mode, String name, Integer count) {}

  public record Region(
      Integer order,
      Integer rank,
      String regionCode,
      String regionName,
      String gu,
      Integer score,
      String mode,
      String modeName,
      String action,
      Long cost) {}

  public record Skipped(
      Integer rank,
      String regionCode,
      String regionName,
      String gu,
      Integer score,
      String mode,
      String modeName,
      String action,
      Long cost,
      Long budgetLeftAtSkip) {}
}
