package com.example.yongyong.region.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record RegionComparisonResponse(PeriodResponse period, List<Metric> metrics) {

  public record Metric(String key, String label, String unit, Integer region, Integer cityAvg) {}
}
