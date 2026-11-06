package com.eparly.api.subscription.domain;

import com.eparly.api.recurring.domain.RecurringFrequency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Types du suivi. Le statut de la demarche reste distinct du statut du contrat (declaredStatus). */
public final class SubscriptionModels {
    private SubscriptionModels() {
    }

    public enum CancellationStatus {
        PLANNED, REQUESTED, CONFIRMED, ENDED, FAILED, ABANDONED;

        public boolean canMoveTo(CancellationStatus next) {
            return switch (this) {
                case PLANNED -> next == REQUESTED || next == ABANDONED;
                case REQUESTED -> next == CONFIRMED || next == FAILED || next == ABANDONED;
                case CONFIRMED -> next == ENDED || next == FAILED;
                case ENDED, FAILED, ABANDONED -> false;
            };
        }
    }

    public enum CancellationChannel { ONLINE_ACCOUNT, EMAIL, POSTAL_MAIL, PHONE, IN_STORE, OTHER }

    public enum ConfirmationSource { USER_DECLARED, USER_HOLDS_PROVIDER_DOCUMENT }

    public enum VerificationState {
        NOT_APPLICABLE, AWAITING_END_DATE, WAITING_END_DATE, WAITING_NEXT_CYCLE, INSUFFICIENT_DATA,
        NO_CHARGE_OBSERVED, CHARGE_OBSERVED
    }

    public enum DateStatus { CONFIRMED, ESTIMATED, UNKNOWN }

    public record Cancellation(UUID id, UUID subscriptionId, CancellationStatus status, Instant decidedAt, LocalDate requestedOn,
                               CancellationChannel channel, String requestReference, LocalDate providerEndDate, LocalDate confirmedOn,
                               ConfirmationSource confirmationSource, String confirmationReference, boolean providerVerified,
                               String notes, Long baselineAmountMinor, RecurringFrequency baselineFrequency, String baselineCurrency,
                               long version, Instant updatedAt) {
    }

    public record LedgerRow(UUID id, UUID accountId, LocalDate date, long amountMinor, String currency, String label,
                            UUID categoryId, String categoryName) {
    }

    /** Cout effectif : le declare prime sur l'observe. Inconnu reste inconnu, jamais zero. */
    public record Cost(Long amountMinor, RecurringFrequency frequency, String currency) {
        public boolean known() {
            return amountMinor != null && amountMinor > 0 && frequency != null && frequency != RecurringFrequency.UNKNOWN;
        }

        public long annualMinor() {
            return switch (frequency) {
                case WEEKLY -> amountMinor * 52;
                case MONTHLY -> amountMinor * 12;
                case QUARTERLY -> amountMinor * 4;
                case YEARLY -> amountMinor;
                case UNKNOWN -> 0;
            };
        }

        public long monthlyMinor() {
            return BigDecimal.valueOf(annualMinor()).divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();
        }
    }

    public static LocalDate next(LocalDate date, RecurringFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
            case UNKNOWN -> throw new IllegalArgumentException("Unknown frequency has no next date");
        };
    }
}
