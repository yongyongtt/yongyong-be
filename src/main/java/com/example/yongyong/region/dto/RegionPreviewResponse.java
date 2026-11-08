package com.example.yongyong.region.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record RegionPreviewResponse(
    PeriodResponse period,
    PeriodResponse typePeriod,
    String regionCode,
    String regionName,
    String gu,
    Integer score,
    Integer rank,
    Type type,
    Boolean isKeep,
    List<Recommended> recommended,
    String summary) {

  public record Type(String name, String desc) {}

  public record Recommended(String mode, String name) {}
}
