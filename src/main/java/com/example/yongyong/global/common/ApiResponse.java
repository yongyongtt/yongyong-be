package com.example.yongyong.global.common;

import com.example.yongyong.global.error.ErrorCode;

public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
    }
}
