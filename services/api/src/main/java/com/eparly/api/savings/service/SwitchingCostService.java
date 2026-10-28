package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferPricing;
import org.springframework.stereotype.Service;

/**
 * Calcul des frais de changement (résiliation, activation, autres frais).
 * Les frais inconnus ne sont jamais substitués par zéro.
 */
@Service
public class SwitchingCostService {

    /**
     * Calcule les frais totaux de changement à partir des frais connus.
     * Les frais inconnus sont ignorés dans le total mais listés séparément.
     *
     * @param cancellationFee frais de résiliation connu (peut être null)
     * @param activationFee frais d'activation de l'offre alternative (peut être null)
     * @param otherFees autres frais obligatoires (peut être null)
     * @return le total des frais connus (0 si aucun n'est connu)
     */
    public Long calculateTotalSwitchingCost(Long cancellationFee, Long activationFee, Long otherFees) {
        Long total = 0L;
        if (cancellationFee != null) {
            if (cancellationFee < 0) throw new IllegalArgumentException("cancellationFee must be non-negative");
            total = Math.addExact(total, cancellationFee);
        }
        if (activationFee != null) {
            if (activationFee < 0) throw new IllegalArgumentException("activationFee must be non-negative");
            total = Math.addExact(total, activationFee);
        }
        if (otherFees != null) {
            if (otherFees < 0) throw new IllegalArgumentException("otherFees must be non-negative");
            total = Math.addExact(total, otherFees);
        }
        return total;
    }

    /**
     * Calcule les frais totaux avec détection des frais inconnus.
     * Retourne toujours le total des frais connus (jamais null) et la liste des frais manquants.
     * L'appelant doit vérifier missingFees pour déterminer si l'estimation est fiable.
     */
    public SwitchingCostResult calculateWithMissingInfo(Long cancellationFee, Long activationFee, Long otherFees) {
        Long total = 0L;
        java.util.List<String> missing = new java.util.ArrayList<>();

        if (cancellationFee != null) {
            if (cancellationFee < 0) throw new IllegalArgumentException("cancellationFee must be non-negative");
            total = Math.addExact(total, cancellationFee);
        } else {
            missing.add("Frais de résiliation");
        }

        if (activationFee != null) {
            if (activationFee < 0) throw new IllegalArgumentException("activationFee must be non-negative");
            total = Math.addExact(total, activationFee);
        } else {
            missing.add("Frais d'activation");
        }

        if (otherFees != null) {
            if (otherFees < 0) throw new IllegalArgumentException("otherFees must be non-negative");
            total = Math.addExact(total, otherFees);
        } else {
            missing.add("Autres frais");
        }

        return new SwitchingCostResult(total, missing);
    }

    public record SwitchingCostResult(Long totalSwitchingCostMinor, java.util.List<String> missingFees) {
    }
}
