package com.example.yongyong.region.dto;

import java.util.List;

public record RegionComparisonResponse(List<Metric> metrics) {

  public record Metric(String key, String label, String unit, Integer region, Integer cityAvg) {}
}
