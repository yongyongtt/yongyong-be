package com.example.yongyong.recommendation.controller;

import com.example.yongyong.common.response.ApiResponse;
import com.example.yongyong.recommendation.dto.RecommendationResponse;
import com.example.yongyong.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 추천")
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendationService;

  @Operation(summary = "AI 추천 해결방안 조회")
  @GetMapping("/{regionCode}/recommendation")
  public ApiResponse<RecommendationResponse> getRecommendation(
      @Parameter(description = "행정동 코드 10자리", example = "4146125000")
          @PathVariable
          @Pattern(regexp = "\\d{10}")
          String regionCode,
      @Parameter(description = "기준 연도", example = "2025") @RequestParam Integer year) {
    return ApiResponse.ok(recommendationService.getRecommendation(regionCode, year));
  }
}
