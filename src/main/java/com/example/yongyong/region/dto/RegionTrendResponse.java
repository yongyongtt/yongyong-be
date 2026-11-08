package com.example.yongyong.region.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record RegionTrendResponse(PeriodResponse period, List<Point> points) {

  public record Point(
      Integer year,
      Integer month,
      Integer unmatchedRate,
      Integer requestCount,
      Integer matchedCount,
      Integer cityAvgRate) {}
}
