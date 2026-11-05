package com.example.yongyong.region.dto;

import java.util.List;

public record RegionDetailResponse(
    Header header, Period period, Reliability reliability, Type type) {

  public record Header(
      String regionCode, String regionName, String gu, Integer score, Integer rank) {}

  public record Period(Integer year, Integer month, List<Integer> availableMonths) {}

  public record Reliability(Boolean low, String note) {}

  public record Type(
      String name, String desc, List<String> chips, List<SameTypeRegion> sameTypeRegions) {}

  public record SameTypeRegion(String regionCode, String regionName) {}
}
