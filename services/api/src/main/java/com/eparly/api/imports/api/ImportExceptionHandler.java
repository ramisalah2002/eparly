package com.eparly.api.imports.api;

import com.eparly.api.imports.dto.ImportErrorResponse;
import com.eparly.api.imports.dto.ImportRejectedResponse;
import com.eparly.api.imports.service.ImportRejectedException;
import com.eparly.api.imports.service.ImportRequestException;
import com.eparly.api.imports.validation.ImportFileException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Erreurs propres aux imports. Les messages sont generiques : jamais de libelle, montant, chemin ou stack trace.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImportExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportExceptionHandler.class);

    @ExceptionHandler(ImportFileException.class)
    ResponseEntity<ImportErrorResponse> handleFile(ImportFileException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.getCode()) {
            case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case BINARY_FILE, NOT_CSV -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case EMPTY_FILE, NO_DATA_ROWS, INVALID_ENCODING -> HttpStatus.BAD_REQUEST;
            case TOO_MANY_ROWS, TOO_MANY_COLUMNS, FIELD_TOO_LONG, MALFORMED_CSV, PROCESSING_TIMEOUT ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
        };
        log.info("Import file rejected: code={}", exception.getCode());
        return response(status, exception.getCode().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(ImportRequestException.class)
    ResponseEntity<ImportErrorResponse> handleRequest(ImportRequestException exception, HttpServletRequest request) {
        log.info("Import request rejected: code={}", exception.getCode());
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(ImportRejectedException.class)
    ResponseEntity<ImportRejectedResponse> handleRejected(ImportRejectedException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_CONTENT;
        return ResponseEntity.status(status).body(new ImportRejectedResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), exception.getMessage(),
                request.getRequestURI(), exception.getImportJobId(), exception.getErrors(),
                exception.getIssueCount(), exception.isTruncated()));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ImportErrorResponse> handleLockTimeout(HttpServletRequest request) {
        log.warn("Import lock wait timed out");
        return response(HttpStatus.CONFLICT, "IMPORT_IN_PROGRESS",
                "Un autre import est en cours sur ce compte : reessayez dans un instant", request);
    }

    private ResponseEntity<ImportErrorResponse> response(
            HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ImportErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), code));
    }
}
