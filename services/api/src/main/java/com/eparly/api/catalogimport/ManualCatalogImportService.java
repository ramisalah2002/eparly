package com.eparly.api.catalogimport;

import com.eparly.api.offer.admin.domain.CatalogActor;
import com.eparly.api.offer.admin.dto.EvidenceCreateRequest;
import com.eparly.api.offer.admin.dto.OfferDraftRequest;
import com.eparly.api.offer.admin.dto.OfferPatchRequest;
import com.eparly.api.offer.admin.dto.PricingUpsertRequest;
import com.eparly.api.offer.admin.service.CatalogAdminService;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.PromotionPriceType;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualCatalogImportService {

    private final CatalogAdminService adminService;
    private final OfferProviderRepository providerRepository;
    private final CommercialOfferRepository offerRepository;
    private final ObjectMapper objectMapper;
    private final OfferPricingRepository pricingRepository;
    private final TransactionTemplate transaction;
    private final CatalogActor actor;

    public ManualCatalogImportService(CatalogAdminService adminService,
                                     OfferProviderRepository providerRepository,
                                     CommercialOfferRepository offerRepository,
                                     ObjectMapper objectMapper,
                                     CatalogImportProperties properties,
                                     OfferPricingRepository pricingRepository,
                                     PlatformTransactionManager transactionManager) {
        this.adminService = adminService;
        this.providerRepository = providerRepository;
        this.offerRepository = offerRepository;
        this.objectMapper = objectMapper;
        this.pricingRepository = pricingRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        UUID parsed = null;
        if (properties.getActorUserId() != null && !properties.getActorUserId().isBlank()) {
            try {
                parsed = UUID.fromString(properties.getActorUserId());
            } catch (IllegalArgumentException ignored) {
                parsed = null;
            }
        }
        // Le compte configure est le demandeur de l'import, pas son auteur.
        this.actor = CatalogActor.systemImport(parsed);
    }

    public CatalogImportReport importFile(Path file, boolean apply) {
        List<String> errors = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int providersPlanned = 0, providersCreated = 0, providersExisting = 0;
        int offersPlanned = 0, offersCreated = 0, offersExisting = 0, offersSkipped = 0;

        if (file == null || !Files.exists(file)) {
            errors.add("Import file does not exist: " + file);
            return new CatalogImportReport(String.valueOf(file), apply, 0, 0, 0, 0, 0, 0, 0, errors, actions);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readString(file));
        } catch (IOException | JacksonException e) {
            errors.add("Cannot read or parse JSON file " + file + ": " + e.getMessage());
            return new CatalogImportReport(file.toString(), apply, 0, 0, 0, 0, 0, 0, 0, errors, actions);
        }

        boolean isProviderFile = root.has("providers");
        boolean isOfferFile = root.has("offers");
        boolean isRevisionFile = root.has("revisions");

        Map<String, UUID> providerKeys = new LinkedHashMap<>();

        // Providers
        if (isProviderFile) {
            for (JsonNode node : root.get("providers")) {
                try {
                    ProviderImport provider = parseProvider(node);
                    providersPlanned++;
                    UUID providerId = resolveProvider(provider, apply, actions);
                    if (providerId != null) {
                        providerKeys.put(provider.key() == null ? provider.name() : provider.key(), providerId);
                        if (apply) {
                            // determine created vs existing by checking repository again is not needed; action log already says
                        }
                    }
                } catch (Exception e) {
                    errors.add("Provider import error: " + e.getMessage());
                }
            }
        }

        // Counters for created/existing providers are derived from actions
        for (String action : actions) {
            if (action.startsWith("PROVIDER_CREATED")) providersCreated++;
            if (action.startsWith("PROVIDER_EXISTING")) providersExisting++;
        }

        // Offers
        if (isOfferFile) {
            // Load providers from sibling providers.json if present
            if (providerKeys.isEmpty()) {
                loadProviderKeys(file.getParent(), providerKeys);
            }
            for (JsonNode node : root.get("offers")) {
                try {
                    OfferImport offer = parseOffer(node);
                    offersPlanned++;
                    String result = importOffer(offer, providerKeys, apply, actions, errors);
                    switch (result) {
                        case "CREATED" -> offersCreated++;
                        case "EXISTING" -> offersExisting++;
                        case "SKIPPED" -> offersSkipped++;
                        default -> {}
                    }
                } catch (Exception e) {
                    errors.add("Offer import error: " + e.getMessage());
                    offersSkipped++;
                }
            }
        }

        if (isRevisionFile) {
            loadProviderKeys(file.getParent() == null ? null : file.getParent().resolveSibling("v1"), providerKeys);
            for (JsonNode node : root.get("revisions")) {
                offersPlanned++;
                try {
                    if (applyRevision(node, providerKeys, apply, actions)) offersExisting++; else offersSkipped++;
                } catch (Exception e) {
                    errors.add("Revision error: " + e.getMessage());
                    offersSkipped++;
                }
            }
        }

        return new CatalogImportReport(file.toString(), apply, providersPlanned, providersCreated, providersExisting,
                offersPlanned, offersCreated, offersExisting, offersSkipped, errors, actions);
    }

    public List<CatalogImportReport> importDirectory(Path directory, boolean apply) {
        List<CatalogImportReport> reports = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            reports.add(CatalogImportReport.empty(String.valueOf(directory), apply));
            reports.get(0).errors().add("Not a directory: " + directory);
            return reports;
        }
        Path providersFile = directory.resolve("providers.json");
        if (Files.exists(providersFile)) {
            reports.add(importFile(providersFile, apply));
        }
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.getFileName().toString().endsWith("-offers.json") || p.getFileName().toString().endsWith("offers.json"))
                    .sorted()
                    .forEach(p -> {
                        if (p.equals(providersFile)) return;
                        reports.add(importFile(p, apply));
                    });
        } catch (IOException e) {
            CatalogImportReport r = CatalogImportReport.empty(directory.toString(), apply);
            r.errors().add("Cannot list directory: " + e.getMessage());
            reports.add(r);
        }
        return reports;
    }

    private ProviderImport parseProvider(JsonNode node) {
        String name = text(node, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Provider name is required");
        String sector = text(node, "sector");
        if (sector == null || sector.isBlank()) throw new IllegalArgumentException("Provider sector is required");
        // validate enum
        OfferSector.valueOf(sector);
        String website = text(node, "officialWebsite");
        if (website == null || website.isBlank()) throw new IllegalArgumentException("Provider officialWebsite is required");
        return new ProviderImport(
                text(node, "key"),
                name.trim(),
                sector.trim(),
                website.trim(),
                text(node, "country")
        );
    }

    private UUID resolveProvider(ProviderImport provider, boolean apply, List<String> actions) {
        OfferSector sector = OfferSector.valueOf(provider.sector());
        var existing = providerRepository.findByNameIgnoreCaseAndSector(provider.name(), sector);
        if (existing.isPresent()) {
            actions.add("PROVIDER_EXISTING: " + provider.name() + " (" + sector + ")");
            return existing.get().getId();
        }
        if (!apply) {
            actions.add("PROVIDER_PLANNED_CREATE: " + provider.name() + " (" + sector + ")");
            return null;
        }
        var created = adminService.createProvider(actor, provider.name(), sector,
                provider.officialWebsite(), provider.country());
        actions.add("PROVIDER_CREATED: " + provider.name() + " (" + sector + ") id=" + created.id());
        return created.id();
    }

    private OfferImport parseOffer(JsonNode node) {
        String providerKey = text(node, "providerKey");
        if (providerKey == null || providerKey.isBlank()) throw new IllegalArgumentException("Offer providerKey is required");
        String name = text(node, "commercialName");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Offer commercialName is required");
        String sector = text(node, "sector");
        if (sector == null || sector.isBlank()) throw new IllegalArgumentException("Offer sector is required");
        OfferSector.valueOf(sector);
        String territory = text(node, "territory");
        if (territory == null || territory.isBlank()) territory = "FR";

        Map<String, Object> characteristics = new LinkedHashMap<>();
        JsonNode chars = node.get("characteristics");
        if (chars != null && chars.isObject()) {
            chars.properties().forEach(e -> {
                if (e.getValue().isNull()) return;
                if (e.getValue().isNumber()) characteristics.put(e.getKey(), e.getValue().numberValue());
                else if (e.getValue().isBoolean()) characteristics.put(e.getKey(), e.getValue().booleanValue());
                else characteristics.put(e.getKey(), e.getValue().asText());
            });
        }

        OfferImport.PricingImport pricing = null;
        JsonNode pNode = node.get("pricing");
        if (pNode != null && pNode.isObject()) {
            pricing = parsePricing(pNode);
        }

        OfferImport.EvidenceImport evidence = null;
        JsonNode eNode = node.get("evidence");
        if (eNode != null && eNode.isObject()) {
            evidence = parseEvidence(eNode);
        }

        return new OfferImport(
                providerKey, name.trim(), sector.trim(), territory.trim().toUpperCase(),
                characteristics,
                text(node, "eligibilityConditions"),
                text(node, "commitment"),
                dateOrNull(node, "validFrom"),
                dateOrNull(node, "validUntil"),
                pricing, evidence
        );
    }

    private OfferImport.PricingImport parsePricing(JsonNode pNode) {
        return new OfferImport.PricingImport(
                longOrNull(pNode, "amountMinor"),
                text(pNode, "currency"),
                text(pNode, "billingPeriod"),
                longOrNull(pNode, "activationFeeMinor"),
                text(pNode, "otherFees"),
                text(pNode, "promotionPriceType"),
                intOrNull(pNode, "promotionPercent"),
                longOrNull(pNode, "promotionDurationMonths"),
                longOrNull(pNode, "priceAfterPromotionMinor"),
                dateOrNull(pNode, "promotionValidFrom"),
                dateOrNull(pNode, "promotionValidUntil"),
                text(pNode, "discountConditions"),
                boolOrNull(pNode, "taxesIncluded"),
                boolOrNull(pNode, "dataUnknown"),
                text(pNode, "reason")
        );
    }

    private OfferImport.EvidenceImport parseEvidence(JsonNode eNode) {
        return new OfferImport.EvidenceImport(
                text(eNode, "officialUrl"),
                instantOrNull(eNode, "consultedAt"),
                instantOrNull(eNode, "expiresAt"),
                text(eNode, "nature"),
                text(eNode, "provenance"),
                text(eNode, "evidenceScope")
        );
    }

    private String importOffer(OfferImport offer, Map<String, UUID> providerKeys, boolean apply,
                               List<String> actions, List<String> errors) {
        UUID providerId = providerKeys.get(offer.providerKey());
        if (providerId == null) {
            // try find existing by key treated as name
            var existing = providerRepository.findByNameIgnoreCaseAndSector(offer.providerKey(),
                    OfferSector.valueOf(offer.sector()));
            if (existing.isPresent()) {
                providerId = existing.get().getId();
                providerKeys.put(offer.providerKey(), providerId);
            }
        }
        if (providerId == null) {
            if (!apply) {
                actions.add("OFFER_PLANNED_SKIP_PROVIDER_MISSING: " + offer.commercialName());
                return "SKIPPED";
            }
            errors.add("Provider not found for offer: " + offer.commercialName() + " (key=" + offer.providerKey() + ")");
            return "SKIPPED";
        }

        var existingOffer = offerRepository.findByCommercialNameAndProviderId(offer.commercialName(), providerId);
        if (existingOffer.isPresent()) {
            actions.add("OFFER_EXISTING: " + offer.commercialName());
            return "EXISTING";
        }

        if (!apply) {
            actions.add("OFFER_PLANNED_CREATE: " + offer.commercialName());
            return "CREATED";
        }

        // Offre, tarif et preuve forment une seule transaction : un echec ne laisse pas de brouillon partiel.
        UUID resolvedProviderId = providerId;
        return transaction.execute(status -> createOffer(offer, resolvedProviderId, actions));
    }

    private String createOffer(OfferImport offer, UUID providerId, List<String> actions) {
        String characteristicsJson = "{}";
        if (offer.characteristics() != null && !offer.characteristics().isEmpty()) {
            try {
                characteristicsJson = objectMapper.writeValueAsString(offer.characteristics());
            } catch (JacksonException e) {
                throw new IllegalArgumentException("Invalid characteristics JSON");
            }
        }
        var created = adminService.createDraft(actor, new OfferDraftRequest(
                providerId, offer.commercialName(), OfferSector.valueOf(offer.sector()),
                characteristicsJson, offer.eligibilityConditions(), offer.commitment(),
                offer.territory(), offer.validFrom(), offer.validUntil()));
        UUID offerId = created.id();
        actions.add("OFFER_CREATED: " + offer.commercialName() + " id=" + offerId);

        // pricing
        if (offer.pricing() != null) {
            OfferImport.PricingImport p = offer.pricing();
            if (p.amountMinor() == null) {
                actions.add("PRICING_SKIPPED_NO_AMOUNT: " + offer.commercialName());
            } else {
                adminService.upsertPricing(actor, offerId, pricingRequest(p, null));
                actions.add("PRICING_CREATED: " + offer.commercialName());
            }
        }

        // evidence
        if (offer.evidence() != null && offer.evidence().officialUrl() != null) {
            addEvidence(offerId, offer.evidence());
            actions.add("EVIDENCE_ADDED: " + offer.commercialName());
        }

        return "CREATED";
    }

    private PricingUpsertRequest pricingRequest(OfferImport.PricingImport p, Long expectedVersion) {
        OfferBillingPeriod period = p.billingPeriod() == null ? OfferBillingPeriod.MONTHLY
                : OfferBillingPeriod.valueOf(p.billingPeriod());
        PromotionPriceType type = p.promotionPriceType() == null ? null
                : PromotionPriceType.valueOf(p.promotionPriceType());
        return new PricingUpsertRequest(
                expectedVersion, p.amountMinor(), p.currency() == null ? "EUR" : p.currency(), period,
                p.activationFeeMinor(), p.otherFees(), type, p.promotionPercent(),
                p.promotionDurationMonths(), p.priceAfterPromotionMinor(),
                p.promotionValidFrom(), p.promotionValidUntil(), p.discountConditions(),
                p.taxesIncluded() == null ? Boolean.TRUE : p.taxesIncluded(),
                // Une donnee non declaree reste inconnue : elle ne devient jamais fiable par defaut.
                p.dataUnknown() == null ? Boolean.TRUE : p.dataUnknown(),
                p.reason());
    }

    private void addEvidence(UUID offerId, OfferImport.EvidenceImport e) {
        if (e.consultedAt() == null) {
            throw new IllegalArgumentException("evidence.consultedAt is required: the real consultation date is never invented");
        }
        OfferEvidenceScope scope = e.evidenceScope() == null ? OfferEvidenceScope.OFFER
                : OfferEvidenceScope.valueOf(e.evidenceScope());
        adminService.addEvidence(actor, offerId, new EvidenceCreateRequest(
                e.officialUrl(), scope, e.nature() == null ? "Tarif officiel" : e.nature(),
                e.provenance() == null ? "Catalogue manuel V1" : e.provenance(),
                e.consultedAt(), e.expiresAt()));
    }

    /**
     * Corrige une offre existante via les services metier, avec historique et audit. La version attendue rend le
     * rejeu sans effet : le tarif a change, la revision n'est plus appliquee. Retourne vrai si elle a ete traitee.
     */
    private boolean applyRevision(JsonNode node, Map<String, UUID> providerKeys, boolean apply, List<String> actions) {
        String name = text(node, "commercialName");
        OfferSector sector = OfferSector.valueOf(text(node, "sector"));
        UUID providerId = providerKeys.get(text(node, "providerKey"));
        if (providerId == null) {
            providerId = providerRepository.findByNameIgnoreCaseAndSector(text(node, "providerKey"), sector)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found for revision: " + name))
                    .getId();
        }
        var offer = offerRepository.findByCommercialNameAndProviderId(name, providerId)
                .filter(found -> found.getSector() == sector)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found for revision: " + name));
        var pricing = pricingRepository.findByOfferId(offer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Pricing not found for revision: " + name));
        long expected = node.get("expectedPricingVersion").longValue();
        if (pricing.getVersion() != expected) {
            actions.add("REVISION_SKIPPED_VERSION_CHANGED: " + name);
            return false;
        }
        if (!apply) {
            actions.add("REVISION_PLANNED: " + name);
            return true;
        }
        OfferImport.PricingImport p = parsePricing(node.get("pricing"));
        List<OfferImport.EvidenceImport> evidences = new ArrayList<>();
        for (JsonNode evidence : node.get("evidences")) evidences.add(parseEvidence(evidence));
        String characteristics = node.hasNonNull("characteristics") ? node.get("characteristics").toString() : null;
        transaction.executeWithoutResult(status -> {
            adminService.patchOffer(actor, offer.getId(), new OfferPatchRequest(offer.getVersion(), null,
                    characteristics, text(node, "eligibilityConditions"), null, null, null, null));
            adminService.upsertPricing(actor, offer.getId(), pricingRequest(p, expected));
            evidences.forEach(evidence -> addEvidence(offer.getId(), evidence));
        });
        actions.add("REVISION_APPLIED: " + name);
        return true;
    }

    private void loadProviderKeys(Path dir, Map<String, UUID> providerKeys) {
        if (dir == null) return;
        Path pf = dir.resolve("providers.json");
        if (!Files.exists(pf)) return;
        try {
            JsonNode root = objectMapper.readTree(Files.readString(pf));
            if (!root.has("providers")) return;
            for (JsonNode node : root.get("providers")) {
                String key = text(node, "key");
                String name = text(node, "name");
                String sector = text(node, "sector");
                if (name == null || sector == null) continue;
                var existing = providerRepository.findByNameIgnoreCaseAndSector(name, OfferSector.valueOf(sector));
                if (existing.isPresent()) {
                    providerKeys.put(key == null ? name : key, existing.get().getId());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.longValue();
        try { return Long.parseLong(v.asText()); } catch (Exception e) { return null; }
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.intValue();
        try { return Integer.parseInt(v.asText()); } catch (Exception e) { return null; }
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.booleanValue();
        return Boolean.parseBoolean(v.asText());
    }

    private java.time.LocalDate dateOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        return java.time.LocalDate.parse(v.asText());
    }

    private Instant instantOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        return Instant.parse(v.asText());
    }
}