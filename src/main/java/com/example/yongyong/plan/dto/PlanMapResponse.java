package com.example.yongyong.plan.dto;

import com.example.yongyong.common.response.PeriodResponse;
import java.util.List;

public record PlanMapResponse(PeriodResponse period, List<Plan> plans) {

  public record Plan(Integer plan, List<Region> regions) {}

  // category: BUS / DRT / HAPPY_TAXI / SKIPPED / KEEP / NONE, status: INCLUDED / SKIPPED /
  // NOT_CANDIDATE
  public record Region(String regionCode, String category, String categoryName, String status) {}
}
