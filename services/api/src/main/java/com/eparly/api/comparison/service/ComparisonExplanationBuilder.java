package com.eparly.api.comparison.service;

import com.eparly.api.comparison.domain.CriterionResult;
import com.eparly.api.comparison.dto.CriterionEvaluation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ComparisonExplanationBuilder {

    /**
     * Construit une explication globale a partir des resultats de criteres.
     */
    public String buildGlobalExplanation(List<CriterionEvaluation> criteria) {
        long notSatisfied = criteria.stream()
                .filter(c -> c.result() == CriterionResult.NOT_SATISFIED)
                .count();
        long unknown = criteria.stream()
                .filter(c -> c.result() == CriterionResult.UNKNOWN)
                .count();
        long notEligible = criteria.stream()
                .filter(c -> c.result() == CriterionResult.NOT_ELIGIBLE)
                .count();

        StringBuilder sb = new StringBuilder();
        if (notEligible > 0) {
            sb.append(notEligible).append(" condition(s) d'eligibilite non remplie(s). ");
        }
        if (notSatisfied > 0) {
            sb.append(notSatisfied).append(" critere(s) obligatoire(s) non satisfait(s). ");
        }
        if (unknown > 0) {
            sb.append(unknown).append(" information(s) essentielle(s) manquante(s). ");
        }
        if (notEligible == 0 && notSatisfied == 0 && unknown == 0) {
            sb.append("Tous les critères obligatoires sont satisfaits.");
        }
        return sb.toString().trim();
    }

    /**
     * Construit l'explication d'un critère individuel.
     */
    public String explain(CriterionResult result, String code, Object required, Object offered) {
        return switch (result) {
            case SATISFIED -> "Critère '" + code + "' satisfait (offre: " + formatValue(offered) + ").";
            case NOT_SATISFIED -> "Critère '" + code + "' non satisfait. Exige: "
                    + formatValue(required) + ", offre: " + formatValue(offered) + ".";
            case UNKNOWN -> "Critère '" + code + "' inconnu. Exige: "
                    + formatValue(required) + ", offre: information non disponible.";
            case NOT_ELIGIBLE -> "Critère '" + code + "' rend l'offre inéligible. Exige: "
                    + formatValue(required) + ", offre: " + formatValue(offered) + ".";
        };
    }

    private String formatValue(Object value) {
        if (value == null) return "non renseigné";
        if (value instanceof Boolean b) return b ? "oui" : "non";
        return value.toString();
    }

    public List<String> collectReasons(List<CriterionEvaluation> criteria) {
        List<String> reasons = new ArrayList<>();
        for (CriterionEvaluation c : criteria) {
            if (c.result() == CriterionResult.NOT_SATISFIED
                    || c.result() == CriterionResult.UNKNOWN
                    || c.result() == CriterionResult.NOT_ELIGIBLE) {
                reasons.add(c.explanation());
            }
        }
        return reasons;
    }
}
