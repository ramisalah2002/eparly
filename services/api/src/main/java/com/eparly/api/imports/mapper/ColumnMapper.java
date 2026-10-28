package com.eparly.api.imports.mapper;

import com.eparly.api.imports.parser.AmountFormat;
import com.eparly.api.imports.parser.DateParser;
import com.eparly.api.imports.validation.ImportIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Propose un mapping a partir des en-tetes (via les {@link BankProfile}) et valide/resout un mapping fourni
 * par l'utilisateur (nom d'en-tete insensible a la casse et aux accents).
 */
@Component
public class ColumnMapper {

    public record Resolution(ResolvedMapping mapping, List<ImportIssue> issues) {
        public boolean valid() {
            return mapping != null;
        }
    }

    private final List<BankProfile> profiles;

    public ColumnMapper(List<BankProfile> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    /** Premier mapping complet des profils, sinon le mapping partiel le plus rempli, sinon un mapping vide. */
    public ImportMapping suggest(List<String> headers) {
        ImportMapping bestPartial = null;
        int bestScore = -1;
        for (BankProfile profile : profiles) {
            Optional<ImportMapping> candidate = profile.match(headers);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.get().isComplete()) {
                return candidate.get();
            }
            int score = score(candidate.get());
            if (score > bestScore) {
                bestScore = score;
                bestPartial = candidate.get();
            }
        }
        return bestPartial != null ? bestPartial : ImportMapping.empty();
    }

    public Resolution resolve(ImportMapping mapping, List<String> headers) {
        List<ImportIssue> issues = new ArrayList<>();
        Map<Integer, String> usage = new HashMap<>();

        int date = locate(mapping.dateColumn(), "dateColumn", headers, issues, usage, true);
        int valueDate = locate(mapping.valueDateColumn(), "valueDateColumn", headers, issues, usage, false);
        int label = locate(mapping.labelColumn(), "labelColumn", headers, issues, usage, true);
        int amount = locate(mapping.amountColumn(), "amountColumn", headers, issues, usage, false);
        int debit = locate(mapping.debitColumn(), "debitColumn", headers, issues, usage, false);
        int credit = locate(mapping.creditColumn(), "creditColumn", headers, issues, usage, false);
        int currency = locate(mapping.currencyColumn(), "currencyColumn", headers, issues, usage, false);
        int sourceId = locate(mapping.sourceIdColumn(), "sourceIdColumn", headers, issues, usage, false);

        boolean signed = mapping.amountColumn() != null;
        boolean split = mapping.debitColumn() != null || mapping.creditColumn() != null;
        if (signed == split) {
            issues.add(ImportIssue.error(0, null, "MAPPING_AMOUNT_INVALID",
                    "Indiquez soit une colonne de montant, soit une colonne de debit ET une colonne de credit"));
        } else if (split && !mapping.hasDebitCredit()) {
            issues.add(ImportIssue.error(0, null, "MAPPING_AMOUNT_INVALID",
                    "Les colonnes debit et credit doivent etre toutes deux renseignees"));
        }

        String dateFormat = mapping.dateFormat() == null ? DateParser.AUTO : mapping.dateFormat();
        if (!DateParser.AUTO.equalsIgnoreCase(dateFormat) && !DateParser.isSupported(dateFormat)) {
            issues.add(ImportIssue.error(0, null, "MAPPING_DATE_FORMAT_UNSUPPORTED",
                    "Format de date non supporte"));
        } else if (DateParser.AUTO.equalsIgnoreCase(dateFormat)) {
            dateFormat = DateParser.AUTO;
        }

        if (!issues.isEmpty()) {
            return new Resolution(null, List.copyOf(issues));
        }
        AmountFormat amountFormat = mapping.amountFormat() == null ? AmountFormat.AUTO : mapping.amountFormat();
        return new Resolution(new ResolvedMapping(date, valueDate, label, amount, debit, credit, currency, sourceId,
                dateFormat, amountFormat, List.copyOf(headers)), List.of());
    }

    private int locate(String column, String role, List<String> headers, List<ImportIssue> issues,
                       Map<Integer, String> usage, boolean required) {
        if (column == null) {
            if (required) {
                issues.add(ImportIssue.error(0, role, "MAPPING_REQUIRED_MISSING",
                        "La colonne " + role + " est obligatoire"));
            }
            return -1;
        }
        String wanted = HeaderNormalizer.normalize(column);
        int found = -1;
        int matches = 0;
        for (int i = 0; i < headers.size(); i++) {
            if (HeaderNormalizer.normalize(headers.get(i)).equals(wanted) && !wanted.isEmpty()) {
                if (found < 0) {
                    found = i;
                }
                matches++;
            }
        }
        if (matches == 0) {
            issues.add(ImportIssue.error(0, column, "MAPPING_COLUMN_NOT_FOUND",
                    "Colonne introuvable dans le fichier"));
            return -1;
        }
        if (matches > 1) {
            issues.add(ImportIssue.error(0, column, "MAPPING_COLUMN_AMBIGUOUS",
                    "Plusieurs colonnes portent ce nom dans le fichier"));
            return -1;
        }
        if (usage.containsKey(found)) {
            issues.add(ImportIssue.error(0, column, "MAPPING_COLUMN_DUPLICATED",
                    "Une meme colonne ne peut pas servir a deux champs"));
            return -1;
        }
        usage.put(found, role);
        return found;
    }

    private static int score(ImportMapping m) {
        int score = 0;
        for (String value : new String[]{m.dateColumn(), m.labelColumn(), m.amountColumn(), m.debitColumn(),
                m.creditColumn(), m.valueDateColumn(), m.currencyColumn(), m.sourceIdColumn()}) {
            if (value != null) {
                score++;
            }
        }
        return score;
    }
}
