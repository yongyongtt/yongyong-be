package com.example.yongyong.api.test;

import com.example.yongyong.global.common.ApiResponse;
import com.example.yongyong.global.error.BusinessException;
import com.example.yongyong.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 응답과 전역 예외 처리를 확인하기 위한 임시 API.
 */
@Tag(name = "테스트", description = "공통 응답과 예외 처리 확인용 임시 API")
@RestController
@RequestMapping("/api/test")
public class TestController {

    public enum ResultType {
        OK, BUSINESS_ERROR, SERVER_ERROR
    }

    @Operation(summary = "응답 형식 확인 (type 값에 따라 성공 또는 에러 응답)")
    @GetMapping
    public ApiResponse<String> test(@RequestParam ResultType type) {
        return switch (type) {
            case OK -> ApiResponse.ok("정상 응답입니다.");
            case BUSINESS_ERROR -> throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
            case SERVER_ERROR -> throw new IllegalStateException("예외 처리 확인용 서버 오류");
        };
    }
}
