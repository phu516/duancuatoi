package com.example.demo.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static ApiException invalid(String message) { return new ApiException(400, "VALIDATION_ERROR", message); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "Không tìm thấy dữ liệu"); }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "Không đủ quyền"); }
    public static ApiException conflict(String code, String message) { return new ApiException(409, code, message); }
}

