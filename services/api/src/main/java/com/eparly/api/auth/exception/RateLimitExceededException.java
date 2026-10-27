package com.eparly.api.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
