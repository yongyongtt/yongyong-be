package com.example.yongyong.dashboard.dto;

import java.util.List;

public record DashboardResponse(Period period, Summary summary, List<TopRegion> top10) {

  public record Period(
      Integer year,
      Integer month,
      List<Integer> availableYears,
      List<Integer> availableMonths,
      String updatedAt) {}

  public record Summary(long regionCount, long vulnerableCount, Double avgScore) {}

  public record TopRegion(
      Integer rank, String regionCode, String regionName, String gu, Integer score) {}
}
