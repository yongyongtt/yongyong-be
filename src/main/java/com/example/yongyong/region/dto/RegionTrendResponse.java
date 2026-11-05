package com.example.yongyong.region.dto;

import java.util.List;

public record RegionTrendResponse(List<Point> points) {

  public record Point(
      Integer year,
      Integer month,
      Integer unmatchedRate,
      Integer requestCount,
      Integer matchedCount,
      Integer cityAvgRate) {}
}
