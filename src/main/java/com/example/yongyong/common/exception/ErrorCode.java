package com.example.yongyong.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
  REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 지역입니다."),
  PERIOD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기간의 데이터가 아직 없습니다."),
  SUITABILITY_NOT_READY(HttpStatus.NOT_FOUND, "존재하지 않는 달입니다."),
  RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "생성된 AI 추천이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
  ML_RUNNING(HttpStatus.CONFLICT, "이미 실행 중입니다."),
  ML_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "ML 서버에 연결할 수 없습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;
}
