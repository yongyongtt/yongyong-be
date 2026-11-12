package com.example.yongyong.plan.controller;

import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.response.ApiResponse;
import com.example.yongyong.plan.dto.PlanResponse;
import com.example.yongyong.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "통합 최적안")
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

  private final PlanService planService;

  @Operation(summary = "통합 최적안 조회 (1안 효율형, 2안 형평형, 3안 균형형)")
  @GetMapping
  public ApiResponse<PlanResponse> getPlans(@Valid @ParameterObject PeriodQuery query) {
    return ApiResponse.ok(planService.getPlans(query));
  }
}
