package com.eparly.api.imports.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Empreintes de deduplication, version "v1" (documentee dans docs/imports-api.md).
 * <ul>
 *   <li>Avec identifiant source fiable : SHA-256("v1|uid|" + sourceUid).</li>
 *   <li>Sinon : SHA-256("v1|" + base + "|" + n) avec base = dateComptable|montantMineur|devise|libelleNormalise
 *       et n = rang d'occurrence de cette base dans le fichier (1, 2, ...).</li>
 * </ul>
 * Independantes de l'ordre des lignes et du numero de ligne ; la date de valeur est exclue car elle varie entre exports.
 */
@Component
public class FingerprintCalculator {

    public static final String VERSION = "v1";

    public String forSourceUid(String sourceUid) {
        return sha256(VERSION + "|uid|" + sourceUid);
    }

    public String contentBase(LocalDate bookingDate, long amountMinor, String currency, String normalizedLabel) {
        return bookingDate + "|" + amountMinor + "|" + currency + "|" + normalizedLabel;
    }

    public String forContent(String base, int occurrence) {
        return sha256(VERSION + "|" + base + "|" + occurrence);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
