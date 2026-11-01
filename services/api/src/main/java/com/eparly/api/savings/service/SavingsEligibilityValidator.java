package com.eparly.api.savings.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.service.OfferAvailabilityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Valide l'éligibilité d'une offre à une estimation d'économies.
 * Ne fait pas confiance au statut COMPATIBLE transmis par le client.
 * Recalcule ou réutilise de manière sûre l'évaluation réelle.
 */
@Service
public class SavingsEligibilityValidator {

    private final CommercialOfferRepository offerRepository;
    private final OfferAvailabilityService availabilityService;

    public SavingsEligibilityValidator(
            CommercialOfferRepository offerRepository,
            OfferAvailabilityService availabilityService) {
        this.offerRepository = offerRepository;
        this.availabilityService = availabilityService;
    }

    /**
     * Valide qu'une offre existe, est vérifiée, non expirée et éligible à une estimation.
     * Retourne l'offre et son pricing si tout est valide.
     */
    public ValidatedOffer validateOffer(UUID offerId, String territory) {
        CommercialOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));

        OfferAvailabilityService.AvailabilityResult result = availabilityService.evaluate(offer, territory);
        if (!result.available()) {
            return new ValidatedOffer(offer, null, null, result.failure(),
                    availabilityMessage(result.failure(), offer));
        }
        if (!"EUR".equalsIgnoreCase(result.pricing().getCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Devise non supportée: " + result.pricing().getCurrency());
        }
        return new ValidatedOffer(offer, result.pricing(), result.evidence(), null, null);
    }

    public LocalDate currentDate() {
        return availabilityService.currentDate();
    }

    public Optional<OfferEvidence> currentPromotionEvidence(CommercialOffer offer) {
        return availabilityService.currentPromotionEvidence(offer);
    }

    private String availabilityMessage(OfferAvailabilityService.AvailabilityFailure failure, CommercialOffer offer) {
        return switch (failure) {
            case OFFER_NOT_VERIFIED -> "Offre non vérifiée (statut: " + offer.getPublicationStatus() + ")";
            case NOT_YET_AVAILABLE -> "Offre pas encore disponible";
            case OFFER_EXPIRED -> "Offre expirée";
            case WRONG_TERRITORY -> "Offre non disponible dans ce territoire";
            case MISSING_EVIDENCE -> "Aucune preuve de vérification pour cette offre";
            case LATEST_EVIDENCE_NOT_VERIFIED -> "Preuve de vérification non valide";
            case UNKNOWN_VERIFICATION_DATE -> "Date de vérification inconnue ou incohérente";
            case EVIDENCE_EXPIRED -> "Preuve de vérification expirée";
            case STALE_EVIDENCE -> "Offre obsolète (vérification trop ancienne)";
            case INVALID_EVIDENCE_URL -> "La preuve ne référence pas une URL HTTPS valide";
            case UNRELIABLE_PRICING -> "Tarification manquante ou non fiable pour cette offre";
            case PROVIDER_INACTIVE -> "Fournisseur inactif";
            case PROVIDER_SECTOR_MISMATCH -> "Secteur du fournisseur incohérent";
            case OFFER_NOT_FOUND -> "Offre introuvable";
        };
    }

    /**
     * Valide que le statut de compatibilité de l'offre permet une estimation d'économies.
     * Seules les offres COMPATIBLE sont éligibles.
     */
    public void validateCompatibility(ComparisonOfferResult comparisonResult) {
        if (comparisonResult == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Résultat de comparaison manquant");
        }
        if (comparisonResult.status() == ComparisonStatus.INCOMPATIBLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Offre incompatible avec les critères de l'utilisateur");
        }
        if (comparisonResult.status() == ComparisonStatus.NOT_ELIGIBLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Utilisateur non éligible pour cette offre");
        }
        if (comparisonResult.status() == ComparisonStatus.NEEDS_INFORMATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Informations insuffisantes pour établir l'équivalence");
        }
    }

    public record ValidatedOffer(
            CommercialOffer offer,
            OfferPricing pricing,
            com.eparly.api.offer.domain.OfferEvidence evidence,
            OfferAvailabilityService.AvailabilityFailure failure,
            String failureMessage
    ) {
        public boolean available() {
            return failure == null;
        }
    }
}
