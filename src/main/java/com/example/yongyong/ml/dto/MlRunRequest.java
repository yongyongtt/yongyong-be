package com.example.yongyong.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MlRunRequest(
    @Schema(description = "기준 연도 (2000~2100)", example = "2025") @NotNull @Min(2000) @Max(2100)
        Integer year,
    @Schema(description = "월(1~12). 없거나 null이면 연간", example = "1") @Min(1) @Max(12)
        Integer month) {}
