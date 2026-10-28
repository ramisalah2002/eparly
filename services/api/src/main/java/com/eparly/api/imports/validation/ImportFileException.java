package com.eparly.api.imports.validation;

/**
 * Fichier inutilisable dans son ensemble (taille, contenu binaire, structure, ressources excessives).
 * Le message est generique et ne contient jamais de donnees du fichier.
 */
public class ImportFileException extends RuntimeException {

    public enum Code {
        EMPTY_FILE,
        FILE_TOO_LARGE,
        BINARY_FILE,
        NOT_CSV,
        NO_DATA_ROWS,
        INVALID_ENCODING,
        MALFORMED_CSV,
        TOO_MANY_ROWS,
        TOO_MANY_COLUMNS,
        FIELD_TOO_LONG,
        PROCESSING_TIMEOUT
    }

    private final Code code;
    private final int line;

    public ImportFileException(Code code, String message) {
        this(code, message, 0);
    }

    public ImportFileException(Code code, String message, int line) {
        super(message);
        this.code = code;
        this.line = line;
    }

    public Code getCode() {
        return code;
    }

    public int getLine() {
        return line;
    }
}
