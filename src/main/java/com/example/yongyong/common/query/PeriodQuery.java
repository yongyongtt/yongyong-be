package com.example.yongyong.common.query;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PeriodQuery(
    @Parameter(description = "기준 연도", example = "2025", required = true) @NotNull Integer year,
    @Parameter(description = "월(1~12). 없으면 연간", example = "2") @Min(1) @Max(12) Integer month) {

  public static final String UNIT_DONG = "dong";
}
