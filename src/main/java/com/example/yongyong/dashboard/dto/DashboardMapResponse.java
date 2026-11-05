package com.example.yongyong.dashboard.dto;

import java.util.List;

public record DashboardMapResponse(List<MapRegion> regions) {

  public record MapRegion(
      String regionCode, String regionName, String gu, Double score, Integer rank) {}
}
