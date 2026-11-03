package com.eparly.api.catalogsync.normalization;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcule l'identite d'une offre independamment de sa source. L'identite est volontairement
 * prudente : deux variantes aux caracteristiques discriminantes differentes ou inconnues restent
 * distinctes, un doublon manque se revoit a la validation alors qu'une fusion abusive masquerait une offre.
 */
@Service
public class CatalogDeduplicationService {
    private static final Set<String> LEGAL_SUFFIXES =
            Set.of("sa", "sas", "sasu", "sarl", "eurl", "snc", "inc", "ltd", "llc", "gmbh", "bv");

    private final CatalogCategorySpecs specs;

    public CatalogDeduplicationService(CatalogCategorySpecs specs) {
        this.specs = specs;
    }

    public record OfferIdentity(String identityKey, String providerKey, String planKey, String variantKey) {
    }

    /** Retourne null si le fournisseur ou le plan est inconnu : aucune deduplication inter-sources n'est tentee. */
    public OfferIdentity identify(NormalizedCatalogOffer offer, String fallbackProvider) {
        String provider = normalizeProvider(offer.providerName() != null ? offer.providerName() : fallbackProvider);
        String plan = normalizeLabel(offer.name());
        if (provider.isEmpty() || plan.isEmpty() || offer.sector() == null) return null;
        String variant = specs.variantKey(offer.sector(), offer.characteristics());
        String key = sha256(offer.sector().name() + "\n" + provider + "\n" + plan + "\n" + variant);
        return new OfferIdentity(key, provider, plan, variant);
    }

    public String normalizeProvider(String value) {
        return Arrays.stream(normalizeLabel(value).split(" "))
                .filter(token -> !LEGAL_SUFFIXES.contains(token))
                .collect(Collectors.joining(" "));
    }

    static String normalizeLabel(String value) {
        if (value == null) return "";
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
