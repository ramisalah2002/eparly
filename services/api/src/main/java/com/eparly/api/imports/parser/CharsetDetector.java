package com.eparly.api.imports.parser;

import com.eparly.api.imports.validation.ImportFileException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.List;

/**
 * Detection d'encodage : BOM UTF-8, sinon UTF-8 strict, sinon repli 8 bits signale comme ambigu
 * (Windows-1252 et ISO-8859-1 ne sont pas distinguables de facon fiable).
 */
public final class CharsetDetector {

    private static final List<CsvCharset> EIGHT_BIT_CANDIDATES = List.of(CsvCharset.WINDOWS_1252, CsvCharset.ISO_8859_1);

    private CharsetDetector() {
    }

    public record Detection(CsvCharset charset, boolean ambiguous, List<CsvCharset> candidates, int bomLength) {
    }

    public static Detection detect(byte[] data) {
        int bom = bomLength(data);
        if (bom > 0 || isValidUtf8(data, bom)) {
            return new Detection(CsvCharset.UTF_8, false, List.of(), bom);
        }
        return new Detection(CsvCharset.WINDOWS_1252, true, EIGHT_BIT_CANDIDATES, 0);
    }

    /**
     * Decode le fichier avec l'encodage retenu. Un fichier declare UTF-8 mais invalide est refuse
     * plutot que decode avec des caracteres de remplacement.
     */
    public static String decode(byte[] data, CsvCharset charset) {
        int bom = bomLength(data);
        if (charset == CsvCharset.UTF_8) {
            if (!isValidUtf8(data, bom)) {
                throw new ImportFileException(ImportFileException.Code.INVALID_ENCODING,
                        "Le fichier n'est pas un UTF-8 valide : selectionnez l'encodage du fichier");
            }
            return new String(data, bom, data.length - bom, CsvCharset.UTF_8.charset());
        }
        return new String(data, bom, data.length - bom, charset.charset());
    }

    static int bomLength(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return 3;
        }
        return 0;
    }

    private static boolean isValidUtf8(byte[] data, int offset) {
        try {
            CsvCharset.UTF_8.charset().newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, data.length - offset));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
