package com.eparly.api.comparison.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.strategy.ComparisonStrategy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ComparisonCriteriaValidator {

    public void validate(ComparisonRequest request, OfferSector sector) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requête de comparaison invalide");
        }
        if (request.sector() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le secteur est obligatoire");
        }
        if (!request.sector().equals(sector)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le secteur de la requête ne correspond pas au secteur demandé");
        }
        if (request.requirements() == null || request.requirements().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Au moins un critère de besoin est requis");
        }
    }

    public void validate(ComparisonRequest request, OfferSector sector, ComparisonStrategy strategy) {
        validate(request, sector);
        strategy.validateCriteria(request);

        Map<String, ComparisonCriterionDetail> supportedCriteria = new HashMap<>();
        for (ComparisonCriterionDetail criterion : strategy.availableCriteria()) {
            supportedCriteria.put(criterion.code(), criterion);
        }
        Set<String> unknown = request.requirements().keySet().stream()
                .filter(key -> !supportedCriteria.containsKey(key))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Critères non pris en charge: " + String.join(", ", unknown));
        }

        request.requirements().forEach((key, value) ->
                validateValue(key, value, supportedCriteria.get(key)));
    }

    private void validateValue(String code, Object value, ComparisonCriterionDetail criterion) {
        if (value == null) {
            throw invalidValue(code);
        }
        switch (criterion.fieldType()) {
            case "number" -> validateNumber(code, value);
            case "boolean" -> {
                if (!(value instanceof Boolean)) throw invalidValue(code);
            }
            case "string" -> {
                if (!(value instanceof String text) || text.isBlank()) throw invalidValue(code);
                if (criterion.allowedValues() != null && criterion.allowedValues().stream()
                        .noneMatch(allowed -> allowed.equalsIgnoreCase(text))) {
                    throw invalidValue(code);
                }
            }
            default -> throw invalidValue(code);
        }
    }

    private void validateNumber(String code, Object value) {
        if (!(value instanceof Number number)) throw invalidValue(code);
        final int numericValue;
        try {
            numericValue = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalidValue(code);
        }
        int minimum = "minScreens".equals(code) ? 1 : 0;
        if (numericValue < minimum) throw invalidValue(code);
    }

    private ApiException invalidValue(String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Valeur invalide pour le critère: " + code);
    }
}
