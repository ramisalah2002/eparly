package com.eparly.api.imports.validation;

import com.eparly.api.imports.parser.ParsedCsv;
import com.eparly.api.imports.service.ImportProperties;
import com.eparly.api.imports.validation.ImportFileException.Code;
import org.springframework.stereotype.Component;

/**
 * Validation du fichier : l'extension et le Content-Type ne sont pas des preuves, seul le contenu compte.
 */
@Component
public class FileValidator {

    private static final byte[][] BINARY_SIGNATURES = {
            {'P', 'K', 3, 4},                                   // ZIP / XLSX / DOCX
            {'%', 'P', 'D', 'F'},                               // PDF
            {0x1F, (byte) 0x8B},                                // GZIP
            {(byte) 0x89, 'P', 'N', 'G'},                       // PNG
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},            // JPEG
            {'G', 'I', 'F', '8'},                               // GIF
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0},      // OLE2 (XLS / DOC)
            {'R', 'a', 'r', '!'},                               // RAR
            {'7', 'z', (byte) 0xBC, (byte) 0xAF},               // 7z
            {'M', 'Z'},                                         // executable Windows
            {0x7F, 'E', 'L', 'F'}                               // executable ELF
    };

    private final ImportProperties properties;

    public FileValidator(ImportProperties properties) {
        this.properties = properties;
    }

    /** Controles sur les octets bruts, avant tout decodage. */
    public void validateBytes(byte[] data) {
        if (data == null || data.length == 0 || isBlank(data)) {
            throw new ImportFileException(Code.EMPTY_FILE, "Le fichier est vide");
        }
        if (data.length > properties.maxFileBytes()) {
            throw new ImportFileException(Code.FILE_TOO_LARGE, "Le fichier depasse la taille maximale autorisee");
        }
        for (byte[] signature : BINARY_SIGNATURES) {
            if (startsWith(data, signature)) {
                throw new ImportFileException(Code.BINARY_FILE, "Le fichier n'est pas un fichier CSV");
            }
        }
        int control = 0;
        for (byte b : data) {
            if (b == 0) {
                throw new ImportFileException(Code.BINARY_FILE, "Le fichier n'est pas un fichier CSV");
            }
            int v = b & 0xFF;
            if (v < 0x20 && v != '\t' && v != '\n' && v != '\r') {
                control++;
            }
        }
        if (control * 100L > data.length) {
            throw new ImportFileException(Code.BINARY_FILE, "Le fichier n'est pas un fichier CSV");
        }
    }

    /** Structure minimale : un en-tete d'au moins 2 colonnes et une ligne de donnees. */
    public void validateStructure(ParsedCsv csv) {
        if (csv.headers().size() < 2) {
            throw new ImportFileException(Code.NOT_CSV,
                    "Le fichier ne ressemble pas a un CSV : au moins 2 colonnes sont attendues");
        }
        if (csv.rows().isEmpty()) {
            throw new ImportFileException(Code.NO_DATA_ROWS, "Le fichier ne contient aucune ligne de donnees");
        }
    }

    private static boolean isBlank(byte[] data) {
        for (byte b : data) {
            int v = b & 0xFF;
            if (v != ' ' && v != '\t' && v != '\n' && v != '\r') {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
