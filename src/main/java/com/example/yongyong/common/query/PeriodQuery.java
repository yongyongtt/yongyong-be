package com.example.yongyong.common.query;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PeriodQuery(
    @Parameter(description = "기준 연도", example = "2025", required = true) @NotNull Integer year,
    @Parameter(description = "월(1~12). 없으면 연간", example = "2") @Min(1) @Max(12) Integer month) {

  public static final String UNIT_DONG = "dong";

  // ml_ 엔티티는 연간(month NULL) 행을 month 0으로 읽는다 (decisions D16)
  public static final int ANNUAL_MONTH = 0;

  // ml_ 조회용 month: 월이 없으면 연간 행
  public int monthKey() {
    return month == null ? ANNUAL_MONTH : month;
  }
}
