package com.eparly.api.recurring.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cle de rapprochement d'un libelle : retire les mentions bancaires et les references changeantes, garde tous les mots
 * distinctifs. "NETFLIX PREMIUM" et "NETFLIX" restent donc distincts : un doublon manque se corrige, pas une fusion abusive.
 */
public final class LabelKey {
    private static final Set<String> NOISE = Set.of("PRLV", "PRELEVEMENT", "PRELEV", "SEPA", "CB", "CARTE", "PAIEMENT",
            "ACHAT", "FACTURE", "ECHEANCE", "REF", "WWW", "COM", "FR", "DU", "DE", "LE");

    private LabelKey() {
    }

    public static String of(String label) {
        if (label == null) return "";
        String upper = label.toUpperCase(Locale.ROOT);
        String key = Arrays.stream(upper.split("[^A-Z0-9]+"))
                .filter(token -> !token.isEmpty() && !NOISE.contains(token) && token.chars().noneMatch(Character::isDigit))
                .collect(Collectors.joining(" "));
        return key.isEmpty() ? upper.trim() : key;
    }
}
