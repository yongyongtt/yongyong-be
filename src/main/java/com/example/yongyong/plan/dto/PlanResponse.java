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
      Long totalCost,
      Long budgetLeft,
      Integer includedCount,
      Integer skippedCount,
      Integer pop65Total,
      List<ModeCount> modeCounts,
      List<Region> regions,
      List<Skipped> skipped,
      List<ByRegion> byRegion) {}

  public record ModeCount(String mode, String name, Integer count) {}

  public record Region(
      Integer order,
      Integer rank,
      String regionCode,
      String regionName,
      String gu,
      String mode,
      String modeName,
      String action,
      Long cost) {}

  public record Skipped(
      Integer rank,
      String regionCode,
      String regionName,
      String gu,
      String mode,
      String modeName,
      String action,
      Long cost,
      Long budgetLeftAtSkip) {}

  // status: INCLUDED / SKIPPED / NOT_CANDIDATE
  public record ByRegion(String regionCode, String status, String mode, String modeName) {}
}
