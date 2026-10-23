package com.example.demo.dto.response;

import java.util.List;
public record ApiErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String code, String message, List<FieldError> fieldErrors, String traceId) {}
    public record FieldError(String field, String message) {}
    public static ApiErrorResponse of(String code, String message, String traceId) {
        return new ApiErrorResponse(new ErrorDetail(code, message, List.of(), traceId));
    }
}

