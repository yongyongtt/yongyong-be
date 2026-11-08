package com.example.yongyong.region.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record RegionDetailResponse(
    PeriodResponse period,
    PeriodResponse typePeriod,
    Header header,
    Reliability reliability,
    Type type) {

  public record Header(
      String regionCode, String regionName, String gu, Integer score, Integer rank) {}

  public record Reliability(Boolean low, String note) {}

  public record Type(
      String name, String desc, List<String> chips, List<SameTypeRegion> sameTypeRegions) {}

  public record SameTypeRegion(String regionCode, String regionName) {}
}
