package com.example.yongyong.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_WEIGHT(HttpStatus.BAD_REQUEST, "가중치 합이 100이 아니거나 음수가 있습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 지역입니다."),
    SUITABILITY_NOT_READY(HttpStatus.NOT_FOUND, "적합성 평가 결과가 아직 없습니다."),
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "생성된 AI 추천이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
