package com.example.yongyong.dashboard.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record DashboardMapResponse(PeriodResponse period, List<MapRegion> regions) {

  public record MapRegion(
      String regionCode, String regionName, String gu, Integer score, Integer rank) {}
}
