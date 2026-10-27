package com.eparly.api.auth.exception;

import com.eparly.api.auth.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(new ApiErrorResponse(
                        Instant.now(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        exception.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request body is invalid", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()
        ));
    }
}
