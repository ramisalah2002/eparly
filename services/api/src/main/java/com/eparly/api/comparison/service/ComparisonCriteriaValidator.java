package com.eparly.api.comparison.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

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
}
