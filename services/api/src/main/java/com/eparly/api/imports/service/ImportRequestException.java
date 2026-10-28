package com.eparly.api.imports.service;

import org.springframework.http.HttpStatus;

/**
 * Requete d'import refusee, avec un code stable exploitable par le client (ex. CHARSET_REQUIRED, FILE_HASH_MISMATCH).
 */
public class ImportRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ImportRequestException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
