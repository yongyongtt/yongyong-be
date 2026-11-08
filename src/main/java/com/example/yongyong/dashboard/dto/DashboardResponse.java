package com.example.yongyong.dashboard.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record DashboardResponse(
    PeriodResponse period, String updatedAt, Summary summary, List<TopRegion> top10) {

  public record Summary(long regionCount, long vulnerableCount, Double avgScore) {}

  public record TopRegion(
      Integer rank, String regionCode, String regionName, String gu, Integer score) {}
}
