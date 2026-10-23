package com.example.demo.exception;

public class RateLimitException extends ApiException {
    private final long retryAfterSeconds;
    public RateLimitException(long retryAfterSeconds) {
        super(429,"RATE_LIMITED","Thao tác quá thường xuyên. Vui lòng thử lại sau.");
        this.retryAfterSeconds=retryAfterSeconds;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
