package com.example.yongyong.ml.controller;

import com.example.yongyong.common.response.ApiResponse;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import com.example.yongyong.ml.service.MlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ML 실행")
@RestController
@RequestMapping("/api/ml/run")
@RequiredArgsConstructor
public class MlController {

  private final MlService mlService;

  @Operation(
      summary = "ML 실행 요청",
      description = "그 연·월의 ml_ 테이블을 다시 계산한다 (약 15분, 바로 응답). 실행 중이면 409 ML_RUNNING")
  @PostMapping
  public ApiResponse<MlRunStatus> run(@Valid @RequestBody MlRunRequest request) {
    return ApiResponse.ok(mlService.run(request));
  }

  @Operation(summary = "ML 실행 상태 조회", description = "지금 실행 중이거나 마지막으로 끝난 실행 하나")
  @GetMapping("/status")
  public ApiResponse<MlRunStatus> status() {
    return ApiResponse.ok(mlService.status());
  }
}
