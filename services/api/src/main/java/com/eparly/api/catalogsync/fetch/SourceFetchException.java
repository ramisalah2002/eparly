package com.eparly.api.catalogsync.fetch;

/** Echec d'acces a une source. Le message ne contient jamais d'URL ni de secret. */
public class SourceFetchException extends IllegalStateException {
    public enum Code {
        HTTP_RATE_LIMITED,
        HTTP_CLIENT_ERROR,
        HTTP_SERVER_ERROR,
        TIMEOUT,
        NETWORK_ERROR
    }

    private final Code code;

    public SourceFetchException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public SourceFetchException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static SourceFetchException forStatus(int status) {
        Code code = status == 429 ? Code.HTTP_RATE_LIMITED
                : status >= 500 ? Code.HTTP_SERVER_ERROR : Code.HTTP_CLIENT_ERROR;
        return new SourceFetchException(code, "Source returned HTTP " + status);
    }

    public Code code() {
        return code;
    }
}
