package com.eparly.api.comparison.strategy;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.comparison.domain.CriterionResult;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.dto.ComparisonResponse;
import com.eparly.api.comparison.dto.CriterionEvaluation;
import com.eparly.api.comparison.service.ComparisonExplanationBuilder;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MobilePlanComparisonStrategy implements ComparisonStrategy {

    private static final OfferSector SECTOR = OfferSector.MOBILE_PLAN;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ComparisonExplanationBuilder explanationBuilder;

    public MobilePlanComparisonStrategy(ComparisonExplanationBuilder explanationBuilder) {
        this.explanationBuilder = explanationBuilder;
    }

    @Override
    public OfferSector supportedSector() {
        return SECTOR;
    }

    @Override
    public List<ComparisonCriterionDetail> availableCriteria() {
        List<ComparisonCriterionDetail> criteria = new ArrayList<>();
        criteria.add(new ComparisonCriterionDetail(
                "minDataGb", "Données mobiles minimales (Go)", SECTOR, "number",
                null, true, "Quantité minimale de données mobiles requise", null));
        criteria.add(new ComparisonCriterionDetail(
                "requires5G", "5G obligatoire", SECTOR, "boolean",
                null, false, "Indique si la 5G est requise", false));
        criteria.add(new ComparisonCriterionDetail(
                "roamingRequired", "Itinérance requise", SECTOR, "boolean",
                null, false, "Indique si l'itinérance est requise", false));
        criteria.add(new ComparisonCriterionDetail(
                "minRoamingDataGb", "Données en itinérance minimales (Go)", SECTOR, "number",
                null, false, "Quantité minimale de données en itinérance", null));
        criteria.add(new ComparisonCriterionDetail(
                "maxCommitmentMonths", "Engagement maximal acceptable (mois)", SECTOR, "number",
                null, false, "Durée d'engagement maximale acceptable", null));
        criteria.add(new ComparisonCriterionDetail(
                "eligibilityRestrictions", "Restrictions d'éligibilité", SECTOR, "string",
                null, false, "Restrictions d'éligibilité connues", null));
        return criteria;
    }

    @Override
    public void validateCriteria(ComparisonRequest request) {
        Map<String, Object> req = request.requirements();
        if (req == null || req.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Au moins un critère de besoin mobile est requis");
        }
        for (String key : req.keySet()) {
            if (MobilePlanCriterion.fromCode(key) == null) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Critère mobile inconnu: " + key);
            }
        }
    }

    @Override
    public ComparisonOfferResult evaluateOffer(ComparisonRequest request, CommercialOffer offer,
                                               OfferPricing pricing, List<OfferEvidence> evidence) {
        Map<String, Object> req = request.requirements();
        JsonNode chars = parseJson(offer.getCharacteristics());

        List<CriterionEvaluation> criteria = new ArrayList<>();

        // minDataGb (mandatory)
        if (req.containsKey("minDataGb")) {
            Integer required = toInteger(req.get("minDataGb"));
            Integer offered = chars != null ? toInteger(chars.path("dataGb")) : null;
            CriterionResult result = evaluateNumberMin(required, offered);
            criteria.add(new CriterionEvaluation(
                    "minDataGb", "Données mobiles", required, offered, result,
                    explanationBuilder.explain(result, "minDataGb", required, offered),
                    true, evidenceSource(evidence)));
        }

        // requires5G (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("requires5G")) {
            Boolean required = toBoolean(req.get("requires5G"));
            Boolean offered = chars != null ? toBoolean(chars.path("network5G")) : null;
            CriterionResult result = evaluateBooleanRequired(required, offered);
            criteria.add(new CriterionEvaluation(
                    "requires5G", "5G", required, offered, result,
                    explanationBuilder.explain(result, "requires5G", required, offered),
                    true, evidenceSource(evidence)));
        }

        // roamingRequired (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("roamingRequired")) {
            Boolean required = toBoolean(req.get("roamingRequired"));
            Boolean offered = chars != null ? toBoolean(chars.path("roaming")) : null;
            CriterionResult result = evaluateBooleanRequired(required, offered);
            criteria.add(new CriterionEvaluation(
                    "roamingRequired", "Itinérance", required, offered, result,
                    explanationBuilder.explain(result, "roamingRequired", required, offered),
                    true, evidenceSource(evidence)));
        }

        // minRoamingDataGb (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("minRoamingDataGb")) {
            Integer required = toInteger(req.get("minRoamingDataGb"));
            Integer offered = chars != null ? toInteger(chars.path("roamingDataGb")) : null;
            CriterionResult result = evaluateNumberMin(required, offered);
            criteria.add(new CriterionEvaluation(
                    "minRoamingDataGb", "Données itinérance", required, offered, result,
                    explanationBuilder.explain(result, "minRoamingDataGb", required, offered),
                    true, evidenceSource(evidence)));
        }

        // maxCommitmentMonths (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("maxCommitmentMonths")) {
            Integer required = toInteger(req.get("maxCommitmentMonths"));
            String offered = offer.getCommitment();
            CriterionResult result = evaluateCommitment(required, offered);
            criteria.add(new CriterionEvaluation(
                    "maxCommitmentMonths", "Engagement maximal", required, offered, result,
                    explanationBuilder.explain(result, "maxCommitmentMonths", required, offered),
                    true, evidenceSource(evidence)));
        }

        // eligibilityRestrictions (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("eligibilityRestrictions")) {
            String required = toString(req.get("eligibilityRestrictions"));
            String offered = offer.getEligibilityConditions();
            CriterionResult result = evaluateEligibility(required, offered);
            criteria.add(new CriterionEvaluation(
                    "eligibilityRestrictions", "Éligibilité", required, offered, result,
                    explanationBuilder.explain(result, "eligibilityRestrictions", required, offered),
                    true, evidenceSource(evidence)));
        }

        ComparisonStatus status = computeStatus(criteria);
        Integer heuristicScore = computeHeuristicScore(criteria, status);

        return new ComparisonOfferResult(
                offer.getId(), offer.getCommercialName(), offer.getProvider().getName(),
                offer.getSector().name(), status, criteria,
                pricing != null ? pricing.getAmountMinor() : null,
                pricing != null ? pricing.getCurrency() : null,
                pricing != null ? pricing.getBillingPeriod().name() : null,
                pricing != null ? pricing.getPriceAfterPromotionMinor() : null,
                pricing != null ? pricing.getPromotionPercent() : null,
                pricing != null ? pricing.getPromotionDurationMonths() : null,
                pricing != null ? pricing.getActivationFeeMinor() : null,
                offer.getCommitment(), offer.getTerritory(),
                evidence != null && !evidence.isEmpty() ? evidence.get(0).getOfficialUrl() : null,
                evidence != null && !evidence.isEmpty() ? evidence.get(0).getVerifiedAt() : null,
                heuristicScore
        );
    }

    @Override
    public ComparisonResponse buildResponse(ComparisonRequest request, List<ComparisonOfferResult> results) {
        List<ComparisonOfferResult> compatible = new ArrayList<>();
        List<ComparisonOfferResult> incompatible = new ArrayList<>();
        List<ComparisonOfferResult> needsInfo = new ArrayList<>();
        List<ComparisonOfferResult> notEligible = new ArrayList<>();

        for (ComparisonOfferResult r : results) {
            switch (r.status()) {
                case COMPATIBLE -> compatible.add(r);
                case INCOMPATIBLE -> incompatible.add(r);
                case NEEDS_INFORMATION -> needsInfo.add(r);
                case NOT_ELIGIBLE -> notEligible.add(r);
            }
        }

        return new ComparisonResponse(
                request.subscriptionId(), null, null, request.sector(),
                availableCriteria(), compatible, incompatible, needsInfo, notEligible,
                results.size(), Instant.now(), false,
                "Le catalogue n'est pas exhaustif. Les résultats sont basés sur les offres vérifiées disponibles. Aucune rémunération d'affiliation ne influence le classement.",
                Instant.now()
        );
    }

    /* ---------- Évaluation privée ---------- */

    private CriterionResult evaluateNumberMin(Integer required, Integer offered) {
        if (offered == null) return CriterionResult.UNKNOWN;
        return offered >= required ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
    }

    private CriterionResult evaluateBooleanRequired(Boolean required, Boolean offered) {
        if (!Boolean.TRUE.equals(required)) return CriterionResult.SATISFIED;
        if (offered == null) return CriterionResult.UNKNOWN;
        return offered ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
    }

    private CriterionResult evaluateCommitment(Integer maxMonths, String offeredCommitment) {
        if (maxMonths == null) return CriterionResult.SATISFIED;
        if (offeredCommitment == null || offeredCommitment.isBlank()) return CriterionResult.UNKNOWN;
        String lower = offeredCommitment.toLowerCase();
        if (lower.contains("aucun") || lower.contains("sans") || lower.contains("free")) {
            return CriterionResult.SATISFIED;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(offeredCommitment);
        if (m.find()) {
            int months = Integer.parseInt(m.group(1));
            return months <= maxMonths ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
        }
        return CriterionResult.UNKNOWN;
    }

    private CriterionResult evaluateEligibility(String required, String offered) {
        if (offered == null || offered.isBlank()) return CriterionResult.UNKNOWN;
        String lower = offered.toLowerCase();
        if (lower.contains("aucun") || lower.contains("sans restriction") || lower.contains("open")) {
            return CriterionResult.SATISFIED;
        }
        // Si des restrictions existent et que l'utilisateur en a demandé aucune, c'est non satisfait
        if (required != null && (required.equalsIgnoreCase("aucun") || required.equalsIgnoreCase("none"))) {
            return CriterionResult.NOT_SATISFIED;
        }
        return CriterionResult.SATISFIED;
    }

    private ComparisonStatus computeStatus(List<CriterionEvaluation> criteria) {
        boolean hasMandatoryNotSatisfied = false;
        boolean hasMandatoryUnknown = false;
        for (CriterionEvaluation c : criteria) {
            if (c.mandatory()) {
                if (c.result() == CriterionResult.NOT_SATISFIED || c.result() == CriterionResult.NOT_ELIGIBLE) {
                    hasMandatoryNotSatisfied = true;
                }
                if (c.result() == CriterionResult.UNKNOWN) {
                    hasMandatoryUnknown = true;
                }
            }
        }
        if (hasMandatoryNotSatisfied) return ComparisonStatus.INCOMPATIBLE;
        if (hasMandatoryUnknown) return ComparisonStatus.NEEDS_INFORMATION;
        return ComparisonStatus.COMPATIBLE;
    }

    private Integer computeHeuristicScore(List<CriterionEvaluation> criteria, ComparisonStatus status) {
        if (status == ComparisonStatus.INCOMPATIBLE || status == ComparisonStatus.NOT_ELIGIBLE) return 0;
        if (status == ComparisonStatus.NEEDS_INFORMATION) return 30;
        int satisfied = 0, total = 0;
        for (CriterionEvaluation c : criteria) {
            if (c.mandatory()) {
                total++;
                if (c.result() == CriterionResult.SATISFIED) satisfied++;
            }
        }
        if (total == 0) return 50;
        return (int) Math.round((double) satisfied / total * 100);
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private String toString(Object v) {
        return v == null ? null : v.toString();
    }

    private Boolean toBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase();
        return switch (s) {
            case "true", "1", "oui", "yes" -> true;
            case "false", "0", "non", "no" -> false;
            default -> null;
        };
    }

    private String evidenceSource(List<OfferEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return null;
        return evidence.get(0).getOfficialUrl();
    }

    /* ---------- Enum interne ---------- */

    private enum MobilePlanCriterion {
        MIN_DATA_GB("minDataGb"),
        REQUIRES_5G("requires5G"),
        ROAMING_REQUIRED("roamingRequired"),
        MIN_ROAMING_DATA_GB("minRoamingDataGb"),
        MAX_COMMITMENT_MONTHS("maxCommitmentMonths"),
        ELIGIBILITY_RESTRICTIONS("eligibilityRestrictions");

        private final String code;
        MobilePlanCriterion(String code) { this.code = code; }
        static MobilePlanCriterion fromCode(String code) {
            for (MobilePlanCriterion c : values()) {
                if (c.code.equals(code)) return c;
            }
            return null;
        }
    }
}
