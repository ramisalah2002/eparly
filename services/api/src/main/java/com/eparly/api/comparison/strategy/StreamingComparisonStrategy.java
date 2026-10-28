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
public class StreamingComparisonStrategy implements ComparisonStrategy {

    private static final OfferSector SECTOR = OfferSector.STREAMING;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ComparisonExplanationBuilder explanationBuilder;

    public StreamingComparisonStrategy(ComparisonExplanationBuilder explanationBuilder) {
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
                "minScreens", "Nombre minimal d'écrans simultanés", SECTOR, "number",
                null, true, "Nombre minimum d'écrans simultanés requis", null));
        criteria.add(new ComparisonCriterionDetail(
                "minVideoQuality", "Qualité vidéo minimale", SECTOR, "string",
                List.of("SD", "HD", "4K"), true, "Qualité vidéo minimale requise", null));
        criteria.add(new ComparisonCriterionDetail(
                "adsAccepted", "Publicités acceptées", SECTOR, "boolean",
                null, false, "Indique si l'utilisateur accepte les publicités", false));
        criteria.add(new ComparisonCriterionDetail(
                "offlineDownloadRequired", "Téléchargement hors ligne requis", SECTOR, "boolean",
                null, false, "Indique si le téléchargement hors ligne est requis", false));
        criteria.add(new ComparisonCriterionDetail(
                "maxCommitmentMonths", "Engagement maximal acceptable (mois)", SECTOR, "number",
                null, false, "Durée d'engagement maximale acceptable", null));
        criteria.add(new ComparisonCriterionDetail(
                "requiredContent", "Contenu requis", SECTOR, "string",
                null, false, "Contenu spécifique recherché (non vérifiable automatiquement)", null));
        return criteria;
    }

    @Override
    public void validateCriteria(ComparisonRequest request) {
        Map<String, Object> req = request.requirements();
        if (req == null || req.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Au moins un critère de besoin streaming est requis");
        }
        // valider les clés connues
        for (String key : req.keySet()) {
            if (StreamingCriterion.fromCode(key) == null) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Critère streaming inconnu: " + key);
            }
        }
    }

    @Override
    public ComparisonOfferResult evaluateOffer(ComparisonRequest request, CommercialOffer offer,
                                               OfferPricing pricing, List<OfferEvidence> evidence) {
        Map<String, Object> req = request.requirements();
        JsonNode chars = parseJson(offer.getCharacteristics());

        List<CriterionEvaluation> criteria = new ArrayList<>();

        // minScreens (mandatory)
        if (req.containsKey("minScreens")) {
            Integer required = toInteger(req.get("minScreens"));
            Integer offered = chars != null ? toInteger(chars.path("screens")) : null;
            CriterionResult result = evaluateNumberMin(required, offered);
            criteria.add(new CriterionEvaluation(
                    "minScreens", "Écrans simultanés", required, offered, result,
                    explanationBuilder.explain(result, "minScreens", required, offered),
                    true, evidenceSource(evidence)));
        }

        // minVideoQuality (mandatory)
        if (req.containsKey("minVideoQuality")) {
            String required = toString(req.get("minVideoQuality"));
            String offered = chars != null ? chars.path("videoQuality").asText(null) : null;
            CriterionResult result = evaluateVideoQuality(required, offered);
            criteria.add(new CriterionEvaluation(
                    "minVideoQuality", "Qualité vidéo", required, offered, result,
                    explanationBuilder.explain(result, "minVideoQuality", required, offered),
                    true, evidenceSource(evidence)));
        }

        // adsAccepted (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("adsAccepted")) {
            Boolean required = toBoolean(req.get("adsAccepted"));
            Boolean offered = chars != null ? toBoolean(chars.path("ads")) : null;
            CriterionResult result = evaluateBoolean(required, offered);
            criteria.add(new CriterionEvaluation(
                    "adsAccepted", "Publicités", required, offered, result,
                    explanationBuilder.explain(result, "adsAccepted", required, offered),
                    true, evidenceSource(evidence)));
        }

        // offlineDownloadRequired (obligatoire si l'utilisateur l'a explicitement renseigné)
        if (req.containsKey("offlineDownloadRequired")) {
            Boolean required = toBoolean(req.get("offlineDownloadRequired"));
            Boolean offered = chars != null ? toBoolean(chars.path("offlineDownload")) : null;
            CriterionResult result = evaluateBoolean(required, offered);
            criteria.add(new CriterionEvaluation(
                    "offlineDownloadRequired", "Téléchargement hors ligne", required, offered, result,
                    explanationBuilder.explain(result, "offlineDownloadRequired", required, offered),
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

        // requiredContent (obligatoire si l'utilisateur l'a explicitement renseigné;
        // le contenu n'est pas vérifiable automatiquement -> UNKNOWN)
        if (req.containsKey("requiredContent")) {
            String required = toString(req.get("requiredContent"));
            criteria.add(new CriterionEvaluation(
                    "requiredContent", "Contenu requis", required, null, CriterionResult.UNKNOWN,
                    "Le catalogue de contenus n'est pas vérifiable automatiquement. "
                            + "Vérifiez manuellement que le contenu recherché est disponible.",
                    true, null));
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

    private CriterionResult evaluateVideoQuality(String required, String offered) {
        if (offered == null || offered.isBlank()) return CriterionResult.UNKNOWN;
        int requiredRank = qualityRank(required);
        int offeredRank = qualityRank(offered);
        if (requiredRank < 0 || offeredRank < 0) return CriterionResult.UNKNOWN;
        return offeredRank >= requiredRank ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
    }

    private int qualityRank(String q) {
        return switch (q != null ? q.toUpperCase() : null) {
            case "SD" -> 1;
            case "HD" -> 2;
            case "4K", "UHD" -> 3;
            default -> -1;
        };
    }

    private CriterionResult evaluateBoolean(Boolean required, Boolean offered) {
        if (offered == null) return CriterionResult.UNKNOWN;
        return offered.equals(required) ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
    }

    private CriterionResult evaluateCommitment(Integer maxMonths, String offeredCommitment) {
        if (maxMonths == null) return CriterionResult.SATISFIED;
        if (offeredCommitment == null || offeredCommitment.isBlank()) return CriterionResult.UNKNOWN;
        String lower = offeredCommitment.toLowerCase();
        if (lower.contains("aucun") || lower.contains("sans") || lower.contains("free")) {
            return CriterionResult.SATISFIED;
        }
        // Extraire un nombre de mois depuis la chaine
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(offeredCommitment);
        if (m.find()) {
            int months = Integer.parseInt(m.group(1));
            return months <= maxMonths ? CriterionResult.SATISFIED : CriterionResult.NOT_SATISFIED;
        }
        return CriterionResult.UNKNOWN;
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

    /* ---------- Enum interne pour validation des critères ---------- */

    private enum StreamingCriterion {
        MIN_SCREENS("minScreens"),
        MIN_VIDEO_QUALITY("minVideoQuality"),
        ADS_ACCEPTED("adsAccepted"),
        OFFLINE_DOWNLOAD_REQUIRED("offlineDownloadRequired"),
        MAX_COMMITMENT_MONTHS("maxCommitmentMonths"),
        REQUIRED_CONTENT("requiredContent");

        private final String code;
        StreamingCriterion(String code) { this.code = code; }
        static StreamingCriterion fromCode(String code) {
            for (StreamingCriterion c : values()) {
                if (c.code.equals(code)) return c;
            }
            return null;
        }
    }
}
