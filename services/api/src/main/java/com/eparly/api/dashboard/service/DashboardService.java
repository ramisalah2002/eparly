package com.eparly.api.dashboard.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.dashboard.dto.DashboardSummaryResponse;
import com.eparly.api.dashboard.dto.MonthlyTrendResponse;
import com.eparly.api.dashboard.dto.SpendingByCategoryResponse;
import com.eparly.api.dashboard.repository.DashboardRepository;
import com.eparly.api.auth.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Service de calcul des statistiques du dashboard financier.
 *
 * Règles financières appliquées :
 * - EXPENSE + FEE → dépenses (valeur absolue)
 * - INCOME avec montant positif → revenu confirmé
 * - TRANSFER → exclu des revenus et dépenses ordinaires
 * - REFUND → affiché dans unclassifiedPositive
 * - UNKNOWN positif → non qualifié, jamais compté comme revenu
 * - UNKNOWN négatif → traité comme dépense (via EXPENSE/FEE rule, mais UNKNOWN n'est pas dans cette liste)
 *   → en fait, UNKNOWN négatif n'est ni dépense ni revenu, il reste dans unclassified
 *
 * Sécurité :
 * - Toutes les requêtes sont cloisonnées par userId
 * - accountId optionnel est vérifié pour appartenir à l'utilisateur
 * - Aucune donnée financière n'est journalisée
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardRepository dashboardRepository;
    private final AccountRepository accountRepository;

    public DashboardService(
            DashboardRepository dashboardRepository,
            AccountRepository accountRepository
    ) {
        this.dashboardRepository = dashboardRepository;
        this.accountRepository = accountRepository;
    }

    // -------------------------------------------------------------------------
    // Dashboard Summary
    // -------------------------------------------------------------------------

    /**
     * Retourne le résumé statistique pour la période donnée.
     *
     * @param userId    l'utilisateur authentifié
     * @param startDate début de période (inclusif)
     * @param endDate   fin de période (inclusif)
     * @param accountId filtre optionnel par compte
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            UUID accountId
    ) {
        // Vérifier que le compte appartient à l'utilisateur
        resolveAccountId(userId, accountId);

        Map<String, Object> row = dashboardRepository.findSummary(userId, startDate, endDate, accountId);

        // Si aucune transaction, retourner un résumé vide
        if (row == null) {
            return emptySummary();
        }

        long transactionsCount = toLong(row.get("transactions_count"));
        if (transactionsCount == 0) {
            return emptySummary();
        }

        long totalExpenses = toLong(row.get("total_expenses"));
        long confirmedIncome = toLong(row.get("confirmed_income"));
        long unclassifiedExpenses = toLong(row.get("unclassified_expenses"));
        long unclassifiedPositive = toLong(row.get("unclassified_positive"));
        long uncategorizedCount = toLong(row.get("uncategorized_count"));

        LocalDate firstDate = null;
        Object firstDateObj = row.get("first_date");
        if (firstDateObj instanceof LocalDate ld) {
            firstDate = ld;
        } else if (firstDateObj instanceof java.sql.Date sd) {
            firstDate = sd.toLocalDate();
        }
        LocalDate lastDate = null;
        Object lastDateObj = row.get("last_date");
        if (lastDateObj instanceof LocalDate ld2) {
            lastDate = ld2;
        } else if (lastDateObj instanceof java.sql.Date sd2) {
            lastDate = sd2.toLocalDate();
        }
        String currency = row.get("currency") != null
                ? row.get("currency").toString()
                : "EUR";

        return new DashboardSummaryResponse(
                totalExpenses,
                confirmedIncome,
                unclassifiedExpenses,
                unclassifiedPositive,
                transactionsCount,
                uncategorizedCount,
                firstDate,
                lastDate,
                currency,
                true
        );
    }

    private DashboardSummaryResponse emptySummary() {
        return new DashboardSummaryResponse(
                0L, 0L, 0L, 0L, 0L, 0L, null, null, "EUR", true
        );
    }

    // -------------------------------------------------------------------------
    // Spending by Category
    // -------------------------------------------------------------------------

    /**
     * Retourne la répartition des dépenses par catégorie.
     *
     * @param userId    l'utilisateur authentifié
     * @param startDate début de période (inclusif)
     * @param endDate   fin de période (inclusif)
     * @param accountId filtre optionnel par compte
     */
    @Transactional(readOnly = true)
    public List<SpendingByCategoryResponse> getSpendingByCategory(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            UUID accountId
    ) {
        resolveAccountId(userId, accountId);

        List<Map<String, Object>> rows = dashboardRepository.findSpendingByCategory(
                userId, startDate, endDate, accountId
        );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // Calculer le total pour les pourcentages
        long totalExpenses = rows.stream()
                .mapToLong(row -> toLong(row.get("amount_minor")))
                .sum();

        List<SpendingByCategoryResponse> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID categoryId = null;
            Object catIdObj = row.get("category_id");
            if (catIdObj instanceof UUID uuid) {
                categoryId = uuid;
            } else if (catIdObj != null) {
                // PostgreSQL native query may return org.postgresql.util.PGobject or String
                categoryId = UUID.fromString(catIdObj.toString());
            }
            String categoryName = row.get("category_name") != null
                    ? row.get("category_name").toString()
                    : "Non catégorisé";

            long amount = toLong(row.get("amount_minor"));
            long count = toLong(row.get("transaction_count"));

            double percentage = totalExpenses > 0
                    ? roundPercentage(amount, totalExpenses)
                    : 0.0;

            result.add(new SpendingByCategoryResponse(
                    categoryId,
                    categoryName,
                    amount,
                    count,
                    percentage
            ));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Monthly Trend
    // -------------------------------------------------------------------------

    /**
     * Retourne l'évolution mensuelle sur les N derniers mois.
     *
     * @param months    nombre de mois (1-24)
     * @param userId    l'utilisateur authentifié
     * @param accountId filtre optionnel par compte
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrendResponse> getMonthlyTrend(
            int months,
            UUID userId,
            UUID accountId
    ) {
        if (months < 1 || months > 24) {
            throw new ApiException(BAD_REQUEST, "months must be between 1 and 24");
        }

        resolveAccountId(userId, accountId);

        // Calculer la période couverte
        YearMonth endMonth = YearMonth.now();
        YearMonth startMonth = endMonth.minusMonths(months - 1);
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = endMonth.atEndOfMonth();

        List<Map<String, Object>> rows = dashboardRepository.findMonthlyTrend(
                userId, startDate, endDate, accountId
        );

        // Indexer les résultats par mois
        Map<String, Map<String, Object>> byMonth = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String month = row.get("month").toString();
                byMonth.put(month, row);
            }
        }

        // Construire la liste complète avec les mois vides
        List<MonthlyTrendResponse> result = new ArrayList<>();
        YearMonth current = startMonth;
        for (int i = 0; i < months; i++) {
            String monthKey = current.format(MONTH_FORMATTER);
            Map<String, Object> row = byMonth.get(monthKey);

            if (row != null) {
                result.add(new MonthlyTrendResponse(
                        monthKey,
                        toLong(row.get("expenses")),
                        toLong(row.get("confirmed_income")),
                        toLong(row.get("unclassified_positive")),
                        toLong(row.get("transaction_count"))
                ));
            } else {
                // Mois sans transactions — indicateurs nuls
                result.add(new MonthlyTrendResponse(
                        monthKey, 0L, 0L, 0L, 0L
                ));
            }

            current = current.plusMonths(1);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Vérifie que l'accountId optionnel appartient bien à l'utilisateur.
     * Retourne 404 neutre si le compte est étranger.
     */
    private void resolveAccountId(UUID userId, UUID accountId) {
        if (accountId != null) {
            boolean exists = accountRepository.existsByIdAndUserId(accountId, userId);
            if (!exists) {
                // 404 neutre — ne révèle pas si le compte existe chez un autre utilisateur
                throw new ApiException(NOT_FOUND, "Account not found");
            }
        }
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) {
            return number.longValue();
        }
        // Fallback for unexpected types (e.g., PostgreSQL native types)
        try {
            return Long.parseLong(value.toString().replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            log.warn("Cannot convert value to long: {}", value.getClass().getName());
            return 0L;
        }
    }

    /**
     * Calcule le pourcentage avec arrondi à 2 décimales.
     * Utilise BigDecimal pour éviter les erreurs flottantes.
     */
    private static double roundPercentage(long part, long total) {
        if (total == 0) return 0.0;
        BigDecimal partD = BigDecimal.valueOf(part);
        BigDecimal totalD = BigDecimal.valueOf(total);
        return partD.multiply(BigDecimal.valueOf(100))
                .divide(totalD, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
