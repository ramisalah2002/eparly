package com.eparly.api.imports.api;

import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.imports.dto.ImportMappingDto;
import com.eparly.api.imports.mapper.ImportMapping;
import com.eparly.api.imports.parser.CsvCharset;
import com.eparly.api.imports.parser.DelimiterDetector;
import com.eparly.api.imports.service.ImportCommand;
import com.eparly.api.imports.service.ImportRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Decode les parametres multipart. L'identite de l'utilisateur vient uniquement du JWT valide.
 */
@Component
class ImportRequestParser {

    private static final int MAX_MAPPING_LENGTH = 10_000;

    private final ObjectMapper objectMapper;

    ImportRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    static UUID userId(Jwt jwt) {
        return JwtUserId.from(jwt);
    }

    ImportCommand toCommand(
            Jwt jwt,
            UUID accountId,
            MultipartFile file,
            String mapping,
            String charset,
            String delimiter,
            String fileHash
    ) {
        return new ImportCommand(
                userId(jwt),
                accountId,
                readFile(file),
                parseMapping(mapping),
                parseCharset(charset),
                parseDelimiter(delimiter),
                fileHash == null || fileHash.isBlank() ? null : fileHash.trim().toLowerCase()
        );
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "UNREADABLE_FILE", "Le fichier n'a pas pu etre lu");
        }
    }

    private ImportMapping parseMapping(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        if (json.length() > MAX_MAPPING_LENGTH) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "INVALID_MAPPING", "Le mapping est trop volumineux");
        }
        try {
            return objectMapper.readValue(json, ImportMappingDto.class).toMapping();
        } catch (JacksonException exception) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "INVALID_MAPPING",
                    "Le mapping n'est pas un JSON valide");
        }
    }

    private CsvCharset parseCharset(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return CsvCharset.fromName(value).orElseThrow(() -> new ImportRequestException(
                HttpStatus.BAD_REQUEST, "INVALID_CHARSET", "Encodage non supporte (UTF_8, WINDOWS_1252, ISO_8859_1)"));
    }

    private Character parseDelimiter(String value) {
        try {
            return DelimiterDetector.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "INVALID_DELIMITER",
                    "Separateur non supporte (; , ou tabulation)");
        }
    }
}
