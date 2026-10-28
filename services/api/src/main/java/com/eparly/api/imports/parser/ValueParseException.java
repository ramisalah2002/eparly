package com.eparly.api.imports.parser;

/**
 * Valeur de cellule non convertible. Le message ne reprend jamais la valeur lue.
 */
public class ValueParseException extends Exception {

    private final String code;

    public ValueParseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
