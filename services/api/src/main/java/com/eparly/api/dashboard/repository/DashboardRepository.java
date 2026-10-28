package com.eparly.api.dashboard.repository;

import com.eparly.api.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository dédié aux agrégations SQL pour le dashboard financier.
 *
 * Toutes les requêtes sont cloisonnées par utilisateur via account.user_id
 * et bornées par période. Les agrégations sont exécutées côté PostgreSQL
 * pour éviter de charger les transactions en mémoire Java.
 *
 * Règles financières respectées :
 * - EXPENSE + FEE → dépenses (valeur absolue des montants négatifs)
 * - INCOME positif → revenu confirmé
 * - TRANSFER → exclu des revenus et dépenses ordinaires
 * - REFUND → affiché dans unclassifiedPositive
 * - UNKNOWN positif → non qualifié, pas de revenu supposé
 * - UNKNOWN négatif → traité comme dépense
 */
@Repository
public interface DashboardRepository extends JpaRepository<Transaction, UUID> {

    // -------------------------------------------------------------------------
    // Dashboard Summary
    // -------------------------------------------------------------------------

    /**
     * Agrégation unique pour le résumé du dashboard.
     * Cloisonnée par userId via accounts.user_id.
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type IN ('EXPENSE', 'FEE')
                        THEN ABS(t.amount_minor)
                        ELSE 0
                    END
                ), 0) AS total_expenses,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type = 'INCOME' AND t.amount_minor > 0
                        THEN t.amount_minor
                        ELSE 0
                    END
                ), 0) AS confirmed_income,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type IN ('EXPENSE', 'FEE')
                             AND t.category_id IS NULL
                        THEN ABS(t.amount_minor)
                        ELSE 0
                    END
                ), 0) AS unclassified_expenses,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type IN ('UNKNOWN', 'REFUND')
                             AND t.amount_minor > 0
                        THEN t.amount_minor
                        ELSE 0
                    END
                ), 0) AS unclassified_positive,
                COUNT(*) AS transactions_count,
                COALESCE(SUM(CASE WHEN t.category_id IS NULL THEN 1 ELSE 0 END), 0) AS uncategorized_count,
                MIN(t.booking_date) AS first_date,
                MAX(t.booking_date) AS last_date,
                MAX(t.currency) AS currency
            FROM transactions t
            JOIN accounts a ON t.account_id = a.id
            WHERE a.user_id = :userId
              AND t.booking_date >= :startDate
              AND t.booking_date <= :endDate
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.currency = 'EUR'
            """, nativeQuery = true)
    Map<String, Object> findSummary(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") UUID accountId
    );

    // -------------------------------------------------------------------------
    // Spending by Category
    // -------------------------------------------------------------------------

    /**
     * Répartition des dépenses par catégorie.
     * Les dépenses sans catégorie sont regroupées sous category_id NULL.
     */
    @Query(value = """
            SELECT
                t.category_id,
                c.name AS category_name,
                COALESCE(SUM(ABS(t.amount_minor)), 0) AS amount_minor,
                COUNT(*) AS transaction_count
            FROM transactions t
            JOIN accounts a ON t.account_id = a.id
            LEFT JOIN categories c ON t.category_id = c.id
            WHERE a.user_id = :userId
              AND t.booking_date >= :startDate
              AND t.booking_date <= :endDate
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.transaction_type IN ('EXPENSE', 'FEE')
              AND t.currency = 'EUR'
            GROUP BY t.category_id, c.name
            ORDER BY amount_minor DESC
            """, nativeQuery = true)
    List<Map<String, Object>> findSpendingByCategory(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") UUID accountId
    );

    // -------------------------------------------------------------------------
    // Monthly Trend
    // -------------------------------------------------------------------------

    /**
     * Évolution mensuelle des dépenses et revenus.
     * Retourne uniquement les mois qui ont des transactions.
     * Les mois vides sont complétés côté service.
     */
    @Query(value = """
            SELECT
                TO_CHAR(t.booking_date, 'YYYY-MM') AS month,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type IN ('EXPENSE', 'FEE')
                        THEN ABS(t.amount_minor)
                        ELSE 0
                    END
                ), 0) AS expenses,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type = 'INCOME' AND t.amount_minor > 0
                        THEN t.amount_minor
                        ELSE 0
                    END
                ), 0) AS confirmed_income,
                COALESCE(SUM(
                    CASE
                        WHEN t.transaction_type IN ('UNKNOWN', 'REFUND')
                             AND t.amount_minor > 0
                        THEN t.amount_minor
                        ELSE 0
                    END
                ), 0) AS unclassified_positive,
                COUNT(*) AS transaction_count
            FROM transactions t
            JOIN accounts a ON t.account_id = a.id
            WHERE a.user_id = :userId
              AND t.booking_date >= :startDate
              AND t.booking_date <= :endDate
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.currency = 'EUR'
            GROUP BY TO_CHAR(t.booking_date, 'YYYY-MM')
            ORDER BY month DESC
            """, nativeQuery = true)
    List<Map<String, Object>> findMonthlyTrend(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") UUID accountId
    );
}
