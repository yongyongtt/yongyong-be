package com.example.yongyong.dashboard.controller;

import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.response.ApiResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
import com.example.yongyong.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @Operation(summary = "대시보드 요약 조회")
  @GetMapping
  public ApiResponse<DashboardResponse> getDashboard(@Valid @ParameterObject PeriodQuery query) {
    return ApiResponse.ok(dashboardService.getDashboard(query));
  }
}
