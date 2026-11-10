package com.example.yongyong.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MlRunStatus(
    @Schema(description = "연도. 서버에서 직접 돌린 실행이면 null", example = "2025") Integer year,
    @Schema(description = "월. 연간이거나 서버에서 직접 돌린 실행이면 null", example = "1") Integer month,
    @Schema(description = "NONE / RUNNING / SUCCEEDED / FAILED / BUSY", example = "RUNNING")
        String status,
    @Schema(description = "run_all.py 종료 코드. 실행 중이면 null") Integer exitCode,
    @Schema(description = "시작 시각 (ISO 8601)", example = "2026-11-08T22:30:05+09:00")
        String startedAt,
    @Schema(description = "끝난 시각. 실행 중이면 null") String finishedAt,
    @Schema(description = "걸린 시간(초). 실행 중이면 지금까지", example = "0") Integer elapsedSec,
    @Schema(description = "예상 실행 시간(초)", example = "900") Integer expectedSec) {}
