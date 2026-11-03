package com.eparly.api.subscription.dto;

import com.eparly.api.insights.dto.InsightDtos.CommercialAlternatives;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationChannel;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.ConfirmationSource;
import com.eparly.api.subscription.domain.SubscriptionModels.DateStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.VerificationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrat JSON du suivi. Montants en centimes, jamais additionnes entre devises. */
public final class SubscriptionTrackingDtos {
    private SubscriptionTrackingDtos() {
    }

    public record DetectedSubscription(UUID seriesId, UUID accountId, String label, String currency, RecurringFrequency frequency,
                                       Long usualAmountMinor, Integer occurrenceCount, LocalDate lastPaymentDate,
                                       LocalDate nextEstimatedDate, Integer confidenceScore,
                                       @Schema(description = "Abonnement existant qui pourrait correspondre : a lier, jamais fusionne automatiquement") UUID possibleExistingSubscriptionId) {
    }

    public record LinkSeriesRequest(@NotNull UUID seriesId) {
    }

    public record SubscriptionTransaction(UUID id, UUID accountId, LocalDate date, long amountMinor, String currency, String label, String categoryName) {
    }

    @Schema(description = "matchedBy NAME : aucune serie liee, rapprochement par nom, a verifier")
    public record SubscriptionTransactions(String matchedBy, List<SubscriptionTransaction> transactions) {
    }

    public record SubscriptionEvent(UUID id, String type, String details, Instant occurredAt) {
    }

    public record Renewal(UUID subscriptionId, String name, String kind, LocalDate date,
                          @Schema(description = "CONFIRMED : date saisie par l'utilisateur ; ESTIMATED : calculee") DateStatus dateStatus,
                          Long amountMinor, String currency) {
    }

    public record CategoryCost(String categoryName, long monthlyMinor) {
    }

    public record MonthCost(String month, long observedMinor) {
    }

    public record CurrencyOverview(String currency, int activeCount, long monthlyEstimatedMinor, long annualEstimatedMinor,
                                   List<CategoryCost> byCategory, List<MonthCost> observedByMonth) {
    }

    public record SubscriptionsOverview(List<CurrencyOverview> currencies,
                                        @Schema(description = "Abonnements exclus des totaux : montant ou frequence inconnus") int unknownCostCount) {
    }

    public record PriceIncrease(UUID subscriptionId, String name, long oldAmountMinor, long newAmountMinor, long differenceMinor,
                                String currency, List<UUID> transactionIds) {
    }

    public record PossibleRedundancy(String family, List<UUID> subscriptionIds, List<String> names, String explanation) {
    }

    public record ReviewItem(UUID subscriptionId, String name, String reason, String explanation) {
    }

    public record SubscriptionAnalysis(List<PriceIncrease> priceIncreases, List<PossibleRedundancy> possibleRedundancies,
                                       List<ReviewItem> toReview) {
    }

    public record StartCancellationRequest(@Size(max = 1000) String notes) {
    }

    public record UpdateCancellationRequest(@NotNull Long expectedVersion, CancellationStatus status, LocalDate requestedOn,
                                            CancellationChannel channel, @Size(max = 200) String requestReference,
                                            LocalDate providerEndDate, @Size(max = 1000) String notes) {
    }

    public record ConfirmCancellationRequest(@NotNull Long expectedVersion, @NotNull ConfirmationSource source, @NotNull LocalDate confirmedOn,
                                             @Size(max = 200) String reference, LocalDate providerEndDate) {
    }

    public record CancellationResponse(UUID id, UUID subscriptionId, CancellationStatus status, Instant decidedAt, LocalDate requestedOn,
                                       CancellationChannel channel, String requestReference, LocalDate commitmentEndDate,
                                       LocalDate providerEndDate, LocalDate confirmedOn, ConfirmationSource confirmationSource,
                                       String confirmationReference,
                                       @Schema(description = "Toujours faux : Eparly n'a aucune integration fournisseur") boolean providerVerified,
                                       String notes, long version, Verification verification) {
    }

    public record Verification(VerificationState state, LocalDate referenceDate, int coveredCycles, LocalDate lastImportedDate,
                               List<UUID> chargeTransactionIds, boolean amountChanged, String explanation) {
    }

    public record SuspectCharge(UUID eventId, UUID subscriptionId, String details, Instant detectedAt) {
    }

    public record SubscriptionSavings(UUID subscriptionId, String currency,
                                      @Schema(description = "Si l'abonnement etait resilie ; null si le cout est inconnu") Long theoreticalAnnualMinor,
                                      @Schema(description = "Demarche planifiee ou demandee ; pas une economie acquise") Long plannedAnnualMinor,
                                      @Schema(description = "Resiliation confirmee, par projection") Long afterConfirmationAnnualMinor,
                                      @Schema(description = "Constatee sur des cycles importes sans prelevement ; null sinon") Long observedMinor,
                                      int observedCycles, List<String> assumptions, CommercialAlternatives commercialAlternatives,
                                      Map<String, Object> baseline) {
    }
}
