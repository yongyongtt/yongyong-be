package com.example.yongyong.region.controller;

import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.response.ApiResponse;
import com.example.yongyong.region.dto.RegionPreviewResponse;
import com.example.yongyong.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지역")
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

  private final RegionService regionService;

  @Operation(summary = "지역 미리보기 조회")
  @GetMapping("/{regionCode}/preview")
  public ApiResponse<RegionPreviewResponse> getPreview(
      @Parameter(description = "행정동 코드 10자리", example = "4146125000")
          @PathVariable
          @Pattern(regexp = "\\d{10}")
          String regionCode,
      @Valid @ParameterObject PeriodQuery query) {
    return ApiResponse.ok(regionService.getPreview(regionCode, query));
  }
}
