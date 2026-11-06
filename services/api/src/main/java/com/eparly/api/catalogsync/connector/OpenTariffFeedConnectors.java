package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.extraction.StructuredOfferData;
import com.eparly.api.catalogsync.fetch.FetchResult;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.SourceFetchException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connecteurs des jeux de donnees ouverts publiant de vrais tarifs d'abonnement. Une cellule vide reste
 * inconnue. Les schemas sont verifies : une colonne attendue absente fait echouer la seule source concernee.
 */
@Configuration
public class OpenTariffFeedConnectors {
    public static final String SAVOIR_PLACER_BANKS = "savoirplacer-bank-tariffs";
    public static final String GYM_OBSERVATORY = "labonneseance-gym-subscriptions";
    public static final String MICROBOUSSOLE_INVOICING = "microboussole-free-invoicing";
    public static final String IDFM_TITLES = "idfm-transport-titles";
    public static final String NAOLIB_TARIFFS = "naolib-transport-tariffs";

    @Bean
    CatalogSourceConnector savoirPlacerBanks(OfficialSourceHttpClient http) {
        return new CsvConnector(SAVOIR_PLACER_BANKS, ';', http,
                List.of("banque", "offre", "abonnement_mois_eur", "url_source_principale"), true) {
            @Override
            RawCatalogOffer toOffer(CSVRecord row, CatalogSyncSource source) {
                Long monthly = minor(row.get("abonnement_mois_eur"));
                Long account = minor(row.get("tenue_compte_an_eur"));
                Long card = minor(row.get("carte_an_eur"));
                Map<String, String> components = new LinkedHashMap<>();
                putAmount(components, "tenueCompteAnEur", row.get("tenue_compte_an_eur"));
                putAmount(components, "carteAnEur", row.get("carte_an_eur"));
                putAmount(components, "rejetPrelevementEur", row.get("rejet_prelevement_eur"));
                putAmount(components, "commissionInterventionEur", row.get("commission_intervention_eur"));
                boolean annualFees = (account != null && account > 0) || (card != null && card > 0);
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("accountType", "compte courant");
                if ("oui".equalsIgnoreCase(row.get("repere_agence"))) {
                    // Ligne "compte + carte" servant de repere a la source, hors offre groupee.
                    attributes.put("offerNature", "REFERENCE_BRANCH_BENCHMARK");
                }
                // Sans abonnement mensuel, les frais annuels publies sont le vrai cout fixe : jamais presente a 0 EUR.
                boolean annualOnly = annualFees && (monthly == null || monthly == 0);
                Long amount = annualOnly ? (account == null ? 0 : account) + (card == null ? 0 : card) : monthly;
                String name = row.get("banque") + " - " + row.get("offre");
                return new RawCatalogOffer("BANK:" + row.get("slug") + ":" + row.get("offre"),
                        row.get("url_source_principale"), name, null, null, "EUR", annualOnly ? "YEARLY" : "MONTHLY", null, null, attributes,
                        List.of("Brochure en vigueur au " + row.get("brochure_en_vigueur_au"),
                                "Releve du " + row.get("date_releve") + ", source : savoirplacer.fr"),
                        source.sector(), new StructuredOfferData(row.get("banque"), "FR", amount, null, null, null,
                        null, null, annualFees ? CatalogPriceKind.VARIABLE : null, null, components, null, null,
                        new LinkedHashMap<>(row.toMap())));
            }
        };
    }

    @Bean
    CatalogSourceConnector gymObservatory(OfficialSourceHttpClient http) {
        return new CsvConnector(GYM_OBSERVATORY, ',', http,
                List.of("enseigne", "formule", "cout_annuel_hors_frais_eur", "url_source"), true) {
            @Override
            RawCatalogOffer toOffer(CSVRecord row, CatalogSyncSource source) {
                // Facturation souvent par 4 semaines : le cout annuel publie est conserve, sans conversion maison.
                Map<String, String> components = new LinkedHashMap<>();
                putAmount(components, "prixParPeriodeEur", row.get("prix_par_periode_eur"));
                components.put("periodicite", row.get("periodicite_facturation"));
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("activityType", "salle de sport");
                attributes.put("zone", row.get("perimetre_prix"));
                List<String> conditions = new ArrayList<>();
                if (!row.get("remarque").isBlank()) conditions.add(row.get("remarque"));
                conditions.add("Resiliation : " + row.get("preavis_resiliation") + " ; " + row.get("canal_resiliation"));
                conditions.add("Releve du " + row.get("date_releve") + ", source : La Bonne Seance");
                boolean startingAt = row.get("perimetre_prix").toLowerCase(Locale.ROOT).contains("partir de");
                return new RawCatalogOffer("GYM:" + row.get("enseigne") + ":" + row.get("formule"),
                        row.get("url_source"), row.get("enseigne") + " " + row.get("formule"), null,
                        startingAt ? "a partir de" : null, "EUR", "YEARLY", null, null, attributes, conditions,
                        source.sector(), new StructuredOfferData(row.get("enseigne"), "FR",
                        minor(row.get("cout_annuel_hors_frais_eur")), null, null, null,
                        integer(row.get("engagement_mois")), minor(row.get("frais_inscription_eur")), null, null,
                        components, null, null, new LinkedHashMap<>(row.toMap())));
            }
        };
    }

    @Bean
    CatalogSourceConnector microBoussoleInvoicing(OfficialSourceHttpClient http) {
        return new CsvConnector(MICROBOUSSOLE_INVOICING, ';', http,
                List.of("Éditeur", "Offre", "Prix mensuel HT (€)", "Page de l’éditeur"), true) {
            @Override
            RawCatalogOffer toOffer(CSVRecord row, CatalogSyncSource source) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("productFamily", "facturation electronique");
                List<String> conditions = new ArrayList<>();
                for (String column : List.of("Frais annexes", "Gratuité limitée dans le temps",
                        "Prix ensuite HT par mois (€)", "Volume gratuit", "Limites", "Réservée aux")) {
                    if (!row.get(column).isBlank()) conditions.add(column + " : " + row.get(column));
                }
                conditions.add("Releve du " + row.get("Relevé le") + ", source : MicroBoussole");
                // 0 EUR fiable seulement sans limite, sans duree, sans frais, sans public reserve ni compte impose.
                boolean unconditional = "sans limite".equalsIgnoreCase(row.get("Volume gratuit"))
                        && row.get("Frais annexes").isBlank() && row.get("Gratuité limitée dans le temps").isBlank()
                        && row.get("Prix ensuite HT par mois (€)").isBlank() && row.get("Limites").isBlank()
                        && row.get("Réservée aux").isBlank() && "non".equalsIgnoreCase(row.get("Compte bancaire pro"));
                attributes.put("freeTier", true);
                return new RawCatalogOffer("INVOICE:" + row.get("Éditeur") + ":" + row.get("Offre"),
                        row.get("Page de l’éditeur"), row.get("Éditeur") + " " + row.get("Offre"), null, null,
                        "EUR", "MONTHLY", null, null, attributes, conditions, source.sector(),
                        new StructuredOfferData(row.get("Éditeur"), "FR", minor(row.get("Prix mensuel HT (€)")), null,
                                null, null, null, null, unconditional ? null : CatalogPriceKind.VARIABLE, Boolean.FALSE,
                                Map.of(), null, null, new LinkedHashMap<>(row.toMap())));
            }
        };
    }

    @Bean
    CatalogSourceConnector idfmTitles(OfficialSourceHttpClient http, ObjectMapper mapper) {
        return new RecordsConnector(IDFM_TITLES, http, mapper) {
            @Override
            RawCatalogOffer toOffer(JsonNode row, CatalogSyncSource source) {
                String name = text(row, "product_name");
                String period = period(text(row, "price_period"));
                // Ligne de recette publiee telle quelle et titres a l'unite : pas des abonnements.
                if (name == null || period == null || name.toLowerCase(Locale.ROOT).startsWith("test ")) return null;
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("network", "Ile-de-France Mobilites");
                attributes.put("zone", text(row, "price_indication") == null ? "" : text(row, "price_indication"));
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("price", text(row, "price"));
                raw.put("price_period", text(row, "price_period"));
                return new RawCatalogOffer("IDFM:" + name, text(row, "url"), name, text(row, "short_description"),
                        text(row, "price"), "EUR", period, null, null, attributes,
                        text(row, "price_indication") == null ? List.of() : List.of(text(row, "price_indication")),
                        source.sector(), new StructuredOfferData("Ile-de-France Mobilites", "FR", null, null, null,
                        null, null, null, null, null, Map.of(), null, null, raw));
            }

            private String period(String label) {
                if (label == null) return null;
                return switch (label) {
                    case "par mois" -> "MONTHLY";
                    case "par an" -> "YEARLY";
                    case "par semaine" -> "WEEKLY";
                    case "tous les 3 mois" -> "QUARTERLY";
                    default -> null;
                };
            }
        };
    }

    @Bean
    CatalogSourceConnector naolibTariffs(OfficialSourceHttpClient http, ObjectMapper mapper) {
        return new RecordsConnector(NAOLIB_TARIFFS, http, mapper) {
            @Override
            RawCatalogOffer toOffer(JsonNode row, CatalogSyncSource source) {
                String type = text(row, "type_tarif");
                String label = text(row, "libelle_tarif");
                if (label == null || !"Tarif abonnement".equals(type)) return null;
                String period = label.toLowerCase(Locale.ROOT).contains("mensuel") ? "MONTHLY" : null;
                String amount = text(row, "montant");
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("network", "Naolib");
                attributes.put("zone", "Nantes Metropole");
                attributes.put("passengerProfile", label);
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("montant", amount);
                raw.put("type", type);
                // Le fichier ne donne pas la periode de la formule illimitee : elle reste inconnue, donc candidate.
                return new RawCatalogOffer("NAOLIB:" + label, source.sourceUrl().split("\\?")[0],
                        "Naolib " + label.replaceAll("\\s+", " "), null, null, "EUR", period, null, null, attributes,
                        List.of("Tarif applicable au 01/07/2026"), source.sector(),
                        new StructuredOfferData("Naolib", "FR", minor(amount), null, null, null, null, null, null,
                                null, Map.of(), null, null, raw));
            }
        };
    }

    /** Fichier CSV unique. Les URL d'offre pointent vers les pages officielles des fournisseurs cites. */
    abstract static class CsvConnector implements CatalogSourceConnector {
        private final String key;
        private final char delimiter;
        private final OfficialSourceHttpClient http;
        private final List<String> requiredColumns;
        private final boolean anyOfferDomain;

        CsvConnector(String key, char delimiter, OfficialSourceHttpClient http, List<String> requiredColumns,
                     boolean anyOfferDomain) {
            this.key = key;
            this.delimiter = delimiter;
            this.http = http;
            this.requiredColumns = requiredColumns;
            this.anyOfferDomain = anyOfferDomain;
        }

        abstract RawCatalogOffer toOffer(CSVRecord row, CatalogSyncSource source);

        @Override public String key() { return key; }
        @Override public Readiness readiness() { return Readiness.isReady(); }

        @Override
        public List<String> offerDomains(CatalogSyncSource source) {
            return anyOfferDomain ? List.of("*") : List.of(source.officialDomain());
        }

        @Override
        public CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget) {
            budget.ensureBeforeDeadline();
            FetchResult result = http.fetch(source, source.sourceUrl(), source.etag(), source.lastModified());
            if (result.notModified()) return CollectedSourceBatch.notModified(result.etag(), result.lastModified());
            if (result.statusCode() == 404) throw SourceFetchException.forStatus(404);
            String body = result.body().startsWith("﻿") ? result.body().substring(1) : result.body();
            CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setHeader()
                    .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).get();
            try (CSVParser parser = CSVParser.parse(new StringReader(body), format)) {
                for (String column : requiredColumns) {
                    if (!parser.getHeaderMap().containsKey(column)) {
                        throw new IllegalArgumentException("Tariff file does not match the expected schema");
                    }
                }
                List<RawCatalogOffer> offers = new ArrayList<>();
                for (CSVRecord row : parser) {
                    if (!row.isConsistent()) throw new IllegalArgumentException("Tariff file has a malformed row");
                    offers.add(toOffer(row, source));
                }
                return new CollectedSourceBatch(offers, List.of(), 1, false, !offers.isEmpty(),
                        result.etag(), result.lastModified());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Tariff file could not be parsed", exception);
            }
        }
    }

    /**
     * API Opendatasoft /records, paginee. Les robots.txt d'IDFM et de Nantes Metropole interdisent aujourd'hui /api/
     * aux robots non nommes : ces sources restent desactivees tant que l'editeur n'a pas donne son accord.
     */
    abstract static class RecordsConnector implements CatalogSourceConnector {
        private static final int PAGE_SIZE = 100;
        private static final int MAX_PAGES = 20;
        private final String key;
        private final OfficialSourceHttpClient http;
        private final ObjectMapper mapper;

        RecordsConnector(String key, OfficialSourceHttpClient http, ObjectMapper mapper) {
            this.key = key;
            this.http = http;
            this.mapper = mapper;
        }

        abstract RawCatalogOffer toOffer(JsonNode row, CatalogSyncSource source);

        @Override public String key() { return key; }
        @Override public Readiness readiness() { return Readiness.isReady(); }

        @Override
        public CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget) {
            List<RawCatalogOffer> offers = new ArrayList<>();
            int pages = 0;
            boolean complete = false;
            for (int page = 0; page < MAX_PAGES && !budget.stopping(); page++) {
                budget.ensureBeforeDeadline();
                int offset = page * PAGE_SIZE;
                FetchResult result = http.fetch(source, source.sourceUrl() + "?limit=" + PAGE_SIZE + "&offset=" + offset,
                        null, null);
                if (result.statusCode() == 404) throw SourceFetchException.forStatus(404);
                pages++;
                JsonNode root;
                try {
                    root = mapper.readTree(result.body());
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Records response is not valid JSON");
                }
                JsonNode rows = root.path("results");
                if (!rows.isArray()) throw new IllegalArgumentException("Records response has no results array");
                for (JsonNode row : rows) {
                    RawCatalogOffer offer = toOffer(row, source);
                    if (offer != null) offers.add(offer);
                }
                if (offset + PAGE_SIZE >= root.path("total_count").asLong(0)) {
                    complete = !offers.isEmpty();
                    break;
                }
            }
            return new CollectedSourceBatch(offers, List.of(), pages, false, complete, null, null);
        }

        static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
        }
    }

    static Long minor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replace(',', '.')).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    static Integer integer(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static void putAmount(Map<String, String> target, String name, String value) {
        if (value != null && !value.isBlank()) target.put(name, value.trim());
    }
}
