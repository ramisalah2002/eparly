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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tarifs reglementes d'electricite publies en open data par la Commission de regulation de
 * l'energie : Option_Base.csv, Option_HPHC.csv et Option_Tempo.csv (schema decrit par Notice.txt).
 * Chaque fichier forme une source distincte. Seule la ligne en vigueur de chaque puissance
 * devient une offre candidate ; l'abonnement reste annuel, comme publie, et n'est jamais mensualise.
 */
@Component
public class CreElectricityTariffConnector implements CatalogSourceConnector {
    public static final String KEY = "cre-trv-electricity";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final String PROVIDER = "Tarif reglemente de vente d'electricite";
    private static final String ZONE = "FR_METRO_CONTINENTAL";
    private static final List<String> REQUIRED_COLUMNS =
            List.of("DATE_DEBUT", "DATE_FIN", "P_SOUSCRITE", "PART_FIXE_HT", "PART_FIXE_TTC");

    private final OfficialSourceHttpClient httpClient;
    private final Clock clock;

    public CreElectricityTariffConnector(OfficialSourceHttpClient httpClient, Clock clock) {
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Readiness readiness() {
        return Readiness.isReady();
    }

    @Override
    public CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget) {
        budget.ensureBeforeDeadline();
        FetchResult result = httpClient.fetch(source, source.sourceUrl(), source.etag(), source.lastModified());
        if (result.notModified()) return CollectedSourceBatch.notModified(result.etag(), result.lastModified());
        if (result.statusCode() == 404) throw SourceFetchException.forStatus(404);
        List<RawCatalogOffer> offers = parse(result.body(), source);
        return new CollectedSourceBatch(offers, List.of(), 1, false, !offers.isEmpty(),
                result.etag(), result.lastModified());
    }

    List<RawCatalogOffer> parse(String csv, CatalogSyncSource source) {
        String canonicalUrl = "https://" + source.officialDomain() + URI.create(source.sourceUrl()).getRawPath();
        String body = csv.startsWith("﻿") ? csv.substring(1) : csv;
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader()
                .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).get();
        LocalDate today = LocalDate.now(clock);
        try (CSVParser parser = CSVParser.parse(new StringReader(body), format)) {
            for (String column : REQUIRED_COLUMNS) {
                if (!parser.getHeaderMap().containsKey(column)) {
                    throw new IllegalArgumentException("CRE tariff file does not match the documented schema");
                }
            }
            String option = optionOf(parser.getHeaderMap().keySet().stream().toList());
            List<RawCatalogOffer> offers = new ArrayList<>();
            for (CSVRecord row : parser) {
                if (!row.isConsistent()) {
                    throw new IllegalArgumentException("CRE tariff file contains a malformed row");
                }
                LocalDate from = date(row.get("DATE_DEBUT"));
                LocalDate until = date(row.get("DATE_FIN"));
                boolean inForce = from != null && !from.isAfter(today) && (until == null || !until.isBefore(today));
                if (inForce) offers.add(toOffer(row, option, from, until, canonicalUrl, source));
            }
            return offers;
        } catch (IOException exception) {
            throw new IllegalArgumentException("CRE tariff file could not be parsed", exception);
        }
    }

    private RawCatalogOffer toOffer(CSVRecord row, String option, LocalDate from, LocalDate until,
                                    String canonicalUrl, CatalogSyncSource source) {
        String power = row.get("P_SOUSCRITE");
        Long annualTtcMinor = amountMinor(row.get("PART_FIXE_TTC"));

        Map<String, String> components = new TreeMap<>();
        Map<String, Object> rawRow = new LinkedHashMap<>();
        row.toMap().forEach((column, value) -> {
            rawRow.put(column, value);
            if (column.startsWith("PART_") && decimal(value) != null) {
                components.put(column, decimal(value).toPlainString());
            }
        });
        boolean kwhKnown = components.keySet().stream()
                .anyMatch(column -> column.startsWith("PART_VARIABLE") && column.endsWith("_TTC"));

        Map<String, Object> characteristics = new LinkedHashMap<>();
        characteristics.put("tariffOption", option);
        characteristics.put("powerKva", power);
        characteristics.put("zone", ZONE);
        // Tarif de reference publie par le regulateur : ni une offre concurrente, ni une alternative a proposer.
        characteristics.put("offerNature", "REFERENCE_REGULATED_TARIFF");
        if (kwhKnown) characteristics.put("kwhPricingKnown", true);

        StructuredOfferData structured = new StructuredOfferData(PROVIDER, "FR", annualTtcMinor, null, null, null,
                null, null, CatalogPriceKind.VARIABLE, Boolean.TRUE, components, from, until, rawRow);
        String name = "Tarif Bleu " + optionLabel(option) + " " + power + " kVA";
        return new RawCatalogOffer("TRV-ELEC:" + option + ":" + power, canonicalUrl, name,
                "Tarif reglemente residentiel publie par la CRE. Abonnement annuel TTC ; "
                        + "le cout total depend de la consommation.",
                null, "EUR", "YEARLY", null, null, characteristics,
                List.of("Puissance souscrite : " + power + " kVA"), source.sector(), structured);
    }

    private static String optionOf(List<String> columns) {
        if (columns.contains("PART_VARIABLE_TTC")) return "BASE";
        if (columns.contains("PART_VARIABLE_HCBleu_TTC")) return "TEMPO";
        if (columns.contains("PART_VARIABLE_HC_TTC")) return "HPHC";
        throw new IllegalArgumentException("CRE tariff file does not match the documented schema");
    }

    private static String optionLabel(String option) {
        return switch (option) {
            case "BASE" -> "Option Base";
            case "HPHC" -> "Option Heures pleines Heures creuses";
            default -> "Option Tempo";
        };
    }

    /** Certains TTC sont publies avec sept decimales : la valeur exacte reste dans les composantes. */
    private static Long amountMinor(String value) {
        BigDecimal amount = decimal(value);
        return amount == null ? null : amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Une cellule vide ou invalide reste inconnue : elle n'est jamais convertie en zero. */
    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim().toLowerCase(Locale.ROOT), DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("CRE tariff file contains an invalid date", exception);
        }
    }
}
