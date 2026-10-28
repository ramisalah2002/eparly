package com.eparly.api.comparison.strategy;

import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.comparison.domain.CriterionResult;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.dto.ComparisonResponse;

import java.util.List;
import java.util.UUID;

/**
 * Strategie de comparaison pour un secteur donne.
 * Chaque secteur possede sa propre strategie pour valider les caracteristiques
 * d'une offre commerciale contre les besoins utilisateur.
 */
public interface ComparisonStrategy {

    OfferSector supportedSector();

    /**
     * Retourne la liste des critères disponibles pour ce secteur, avec leurs definitions.
     */
    List<ComparisonCriterionDetail> availableCriteria();

    /**
     * Valide les critères de requête fournis par l'utilisateur.
     * Lance une exception si un critère est invalide ou manquant.
     */
    void validateCriteria(ComparisonRequest request);

    /**
     * Compare une offre commerciale aux besoins utilisateur.
     * Retourne le resultat detaille pour cette offre.
     */
    ComparisonOfferResult evaluateOffer(ComparisonRequest request, com.eparly.api.offer.domain.CommercialOffer offer,
                                        com.eparly.api.offer.domain.OfferPricing pricing,
                                        List<com.eparly.api.offer.domain.OfferEvidence> evidence);

    /**
     * Construit la reponse globale pour une liste d'offres evaluees.
     */
    ComparisonResponse buildResponse(ComparisonRequest request, List<ComparisonOfferResult> results);
}
