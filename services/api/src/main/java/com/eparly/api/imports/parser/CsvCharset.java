package com.eparly.api.imports.parser;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Optional;

public enum CsvCharset {
    UTF_8("UTF-8"),
    WINDOWS_1252("windows-1252"),
    ISO_8859_1("ISO-8859-1");

    private final Charset charset;

    CsvCharset(String javaName) {
        this.charset = Charset.forName(javaName);
    }

    public Charset charset() {
        return charset;
    }

    /** Accepte UTF_8, UTF-8, utf8, WINDOWS_1252, cp1252, ISO_8859_1, latin1... */
    public static Optional<CsvCharset> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "UTF_8", "UTF8" -> Optional.of(UTF_8);
            case "WINDOWS_1252", "WINDOWS1252", "CP1252" -> Optional.of(WINDOWS_1252);
            case "ISO_8859_1", "ISO8859_1", "LATIN1", "LATIN_1" -> Optional.of(ISO_8859_1);
            default -> Optional.empty();
        };
    }
}
