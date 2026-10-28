package com.eparly.api.imports.mapper;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisation des noms d'en-tetes : insensible a la casse, aux accents, a la ponctuation et aux espaces multiples.
 */
public final class HeaderNormalizer {

    private HeaderNormalizer() {
    }

    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
