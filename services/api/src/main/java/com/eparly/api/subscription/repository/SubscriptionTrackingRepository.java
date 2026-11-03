package com.eparly.api.subscription.repository;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.subscription.domain.SubscriptionModels.Cancellation;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationChannel;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.ConfirmationSource;
import com.eparly.api.subscription.domain.SubscriptionModels.LedgerRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SubscriptionTrackingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SubscriptionTrackingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record EventRow(UUID id, UUID subscriptionId, String type, String details, Instant occurredAt) {
    }

    private static final RowMapper<Cancellation> CANCELLATION = (rs, row) -> {
        String channel = rs.getString("channel");
        String source = rs.getString("confirmation_source");
        return new Cancellation(rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
                CancellationStatus.valueOf(rs.getString("status")), rs.getTimestamp("decided_at").toInstant(),
                rs.getObject("requested_on", LocalDate.class), channel == null ? null : CancellationChannel.valueOf(channel),
                rs.getString("request_reference"), rs.getObject("provider_end_date", LocalDate.class),
                rs.getObject("confirmed_on", LocalDate.class), source == null ? null : ConfirmationSource.valueOf(source),
                rs.getString("confirmation_reference"), rs.getBoolean("provider_verified"), rs.getString("notes"),
                rs.getObject("baseline_amount_minor", Long.class), RecurringFrequency.valueOf(rs.getString("baseline_frequency")),
                rs.getString("baseline_currency"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    };

    /** Debits du compte de l'utilisateur, hors virements et remboursements. Le cloisonnement passe par accounts.user_id. */
    public List<LedgerRow> expenseRows(UUID userId, LocalDate from) {
        return jdbc.query("""
                SELECT t.id, t.account_id, t.booking_date, ABS(t.amount_minor) AS amount, t.currency, t.normalized_label,
                       t.category_id, c.name AS category_name
                FROM transactions t JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE a.user_id = ? AND t.booking_date >= ? AND t.amount_minor < 0
                  AND t.transaction_type IN ('EXPENSE', 'FEE', 'UNKNOWN')
                  AND (t.category_id IS NULL OR t.category_id <> '550e8400-e001-0000-0000-00000000000b')
                ORDER BY t.booking_date, t.id
                """, (rs, row) -> new LedgerRow(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("booking_date", LocalDate.class), rs.getLong("amount"), rs.getString("currency"),
                rs.getString("normalized_label"), rs.getObject("category_id", UUID.class), rs.getString("category_name")),
                userId, Date.valueOf(from));
    }

    /** Derniere date importee, par compte : la couverture reelle des imports, tous flux confondus. */
    public Map<UUID, LocalDate> lastImportedDates(UUID userId) {
        Map<UUID, LocalDate> dates = new java.util.HashMap<>();
        jdbc.query("""
                SELECT t.account_id, MAX(t.booking_date) FROM transactions t JOIN accounts a ON a.id = t.account_id
                WHERE a.user_id = ? GROUP BY t.account_id
                """, rs -> {
            dates.put(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
        }, userId);
        return dates;
    }

    public List<UUID> linkedSeriesIds(UUID subscriptionId) {
        return jdbc.queryForList("SELECT series_id FROM subscription_linked_series WHERE subscription_id = ? ORDER BY created_at",
                UUID.class, subscriptionId);
    }

    public boolean seriesAlreadyLinked(UUID seriesId) {
        Integer count = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM subscription_linked_series WHERE series_id = ?)
                     + (SELECT COUNT(*) FROM subscriptions WHERE recurring_series_id = ?)
                """, Integer.class, seriesId, seriesId);
        return count != null && count > 0;
    }

    public void linkSeries(UUID subscriptionId, UUID seriesId) {
        jdbc.update("INSERT INTO subscription_linked_series (series_id, subscription_id) VALUES (?, ?)", seriesId, subscriptionId);
    }

    public Optional<Cancellation> latestCancellation(UUID subscriptionId) {
        return jdbc.query("SELECT * FROM subscription_cancellations WHERE subscription_id = ? ORDER BY created_at DESC, id LIMIT 1",
                CANCELLATION, subscriptionId).stream().findFirst();
    }

    public void insertCancellation(UUID subscriptionId, Instant decidedAt, String notes, Long amount, RecurringFrequency frequency, String currency) {
        jdbc.update("""
                INSERT INTO subscription_cancellations
                    (subscription_id, status, decided_at, notes, baseline_amount_minor, baseline_frequency, baseline_currency)
                VALUES (?, 'PLANNED', ?, ?, ?, ?, ?)
                """, subscriptionId, java.sql.Timestamp.from(decidedAt), notes, amount, frequency.name(), currency);
    }

    /** Verrou optimiste : retourne 0 si la demarche a change depuis sa lecture. */
    public int updateCancellation(Cancellation next, long expectedVersion) {
        return jdbc.update("""
                UPDATE subscription_cancellations
                SET status = ?, requested_on = ?, channel = ?, request_reference = ?, provider_end_date = ?, confirmed_on = ?,
                    confirmation_source = ?, confirmation_reference = ?, notes = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ?
                """, next.status().name(), next.requestedOn(), next.channel() == null ? null : next.channel().name(),
                next.requestReference(), next.providerEndDate(), next.confirmedOn(),
                next.confirmationSource() == null ? null : next.confirmationSource().name(), next.confirmationReference(),
                next.notes(), next.id(), expectedVersion);
    }

    public void event(UUID subscriptionId, String type, Map<String, Object> details, String dedupKey) {
        jdbc.update("""
                INSERT INTO subscription_events (subscription_id, event_type, details, dedup_key)
                VALUES (?, ?, CAST(? AS jsonb), ?) ON CONFLICT (dedup_key) DO NOTHING
                """, subscriptionId, type, mapper.writeValueAsString(details), dedupKey);
    }

    public List<EventRow> events(UUID subscriptionId) {
        return jdbc.query("SELECT id, subscription_id, event_type, details::text, occurred_at FROM subscription_events WHERE subscription_id = ? ORDER BY occurred_at DESC, id",
                (rs, row) -> new EventRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant()), subscriptionId);
    }

    public List<EventRow> postCancellationCharges(UUID userId) {
        return jdbc.query("""
                SELECT e.id, e.subscription_id, e.event_type, e.details::text, e.occurred_at
                FROM subscription_events e JOIN subscriptions s ON s.id = e.subscription_id
                WHERE s.user_id = ? AND e.event_type = 'POST_CANCELLATION_CHARGE' ORDER BY e.occurred_at DESC, e.id
                """, (rs, row) -> new EventRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                rs.getTimestamp(5).toInstant()), userId);
    }
}
