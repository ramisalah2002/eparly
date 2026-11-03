package com.eparly.api.insights.repository;

import com.eparly.api.insights.domain.InsightModels.Budget;
import com.eparly.api.insights.domain.InsightModels.RecommendationStatus;
import com.eparly.api.insights.domain.InsightModels.Row;
import com.eparly.api.insights.domain.InsightModels.Series;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Toutes les lectures sont cloisonnees par accounts.user_id et bornees dans le temps. */
@Repository
public class InsightsRepository {
    private final JdbcTemplate jdbc;

    public InsightsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RecommendationState(RecommendationStatus status, Instant hiddenUntil) {
    }

    public List<Row> transactions(UUID userId, LocalDate from, LocalDate to, UUID accountId) {
        return jdbc.query("""
                SELECT t.id, t.account_id, t.booking_date, t.amount_minor, t.currency, t.normalized_label,
                       t.category_id, c.name AS category_name, t.transaction_type, linked.series_id
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN LATERAL (
                    SELECT rst.series_id FROM recurring_series_transactions rst
                    JOIN recurring_series s ON s.id = rst.series_id
                    WHERE rst.transaction_id = t.id AND s.status <> 'DISMISSED'
                    LIMIT 1
                ) linked ON TRUE
                WHERE a.user_id = ? AND t.booking_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR t.account_id = ?::uuid)
                ORDER BY t.booking_date, t.id
                """, (rs, row) -> new Row(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("booking_date", LocalDate.class), rs.getLong("amount_minor"), rs.getString("currency"),
                rs.getString("normalized_label"), rs.getObject("category_id", UUID.class),
                rs.getString("category_name"), rs.getString("transaction_type"),
                rs.getObject("series_id", UUID.class)),
                userId, from, to, accountId, accountId);
    }

    /** Series du moteur d'abonnements existant : ni ecartees, ni rattachees a un abonnement resilie. */
    public List<Series> activeSeries(UUID userId) {
        return jdbc.query("""
                SELECT s.id, s.normalized_label, s.monthly_equivalent_minor, sub.name AS subscription_name
                FROM recurring_series s
                JOIN accounts a ON a.id = s.account_id
                LEFT JOIN subscriptions sub ON sub.recurring_series_id = s.id
                WHERE a.user_id = ? AND s.status <> 'DISMISSED' AND s.currency = 'EUR'
                  AND (sub.id IS NULL OR (sub.declared_status <> 'CANCELED' AND sub.archived_at IS NULL))
                ORDER BY s.id
                """, (rs, row) -> {
            BigDecimal monthly = rs.getBigDecimal("monthly_equivalent_minor");
            return new Series(rs.getObject("id", UUID.class), rs.getString("normalized_label"),
                    monthly == null ? 0 : Math.abs(monthly.longValue()), rs.getString("subscription_name"));
        }, userId);
    }

    public List<Budget> budgets(UUID userId) {
        return jdbc.query("""
                SELECT b.id, b.category_id, c.name, b.monthly_limit_minor
                FROM category_budgets b JOIN categories c ON c.id = b.category_id
                WHERE b.user_id = ? ORDER BY c.name
                """, (rs, row) -> new Budget(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getLong(4)), userId);
    }

    public Budget upsertBudget(UUID userId, UUID categoryId, long limitMinor) {
        jdbc.update("""
                INSERT INTO category_budgets (user_id, category_id, monthly_limit_minor) VALUES (?, ?, ?)
                ON CONFLICT (user_id, category_id)
                DO UPDATE SET monthly_limit_minor = EXCLUDED.monthly_limit_minor, updated_at = CURRENT_TIMESTAMP
                """, userId, categoryId, limitMinor);
        return budgets(userId).stream().filter(b -> b.categoryId().equals(categoryId)).findFirst().orElseThrow();
    }

    public int deleteBudget(UUID userId, UUID categoryId) {
        return jdbc.update("DELETE FROM category_budgets WHERE user_id = ? AND category_id = ?", userId, categoryId);
    }

    public Map<UUID, RecommendationState> states(UUID userId) {
        Map<UUID, RecommendationState> states = new HashMap<>();
        jdbc.query("SELECT recommendation_id, status, hidden_until FROM insight_recommendation_states WHERE user_id = ?",
                rs -> {
                    Timestamp hidden = rs.getTimestamp(3);
                    states.put(rs.getObject(1, UUID.class), new RecommendationState(
                            RecommendationStatus.valueOf(rs.getString(2)), hidden == null ? null : hidden.toInstant()));
                }, userId);
        return states;
    }

    /** Idempotent et sur en cas de requetes concurrentes : la cle primaire arbitre, la derniere ecriture gagne. */
    public void saveState(UUID userId, UUID recommendationId, RecommendationStatus status, Instant hiddenUntil) {
        jdbc.update("""
                INSERT INTO insight_recommendation_states (user_id, recommendation_id, status, hidden_until)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, recommendation_id)
                DO UPDATE SET status = EXCLUDED.status, hidden_until = EXCLUDED.hidden_until,
                              updated_at = CURRENT_TIMESTAMP
                """, userId, recommendationId, status.name(), hiddenUntil == null ? null : Timestamp.from(hiddenUntil));
    }
}
