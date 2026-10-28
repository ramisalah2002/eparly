package com.eparly.api.savings.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.dto.SubscriptionCostSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Récupère le coût actuel d'un abonnement utilisateur.
 * Priorité : montant déclaré > montant observé > inconnu.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionBaselineService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionBaselineService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Récupère le snapshot de coût actuel pour un abonnement appartenant à l'utilisateur.
     */
    public SubscriptionCostSnapshot getCurrentCost(UUID userId, UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Abonnement introuvable"));

        // Abonnements résiliés ou en pause : pas de coût actif à estimer
        if (sub.getDeclaredStatus() == SubscriptionStatus.CANCELED) {
            return SubscriptionCostSnapshot.canceled(
                    sub.getId(), sub.getName(), sub.getProvider(),
                    sub.getCurrency(), sub.getOrigin(), sub.getStatusChangedAt()
            );
        }
        if (sub.getDeclaredStatus() == SubscriptionStatus.PAUSED) {
            Long observedAmount = null;
            RecurringSeries series = sub.getRecurringSeries();
            if (series != null) {
                observedAmount = series.getUsualAmountMinor();
            }
            return SubscriptionCostSnapshot.paused(
                    sub.getId(), sub.getName(), sub.getProvider(),
                    observedAmount,
                    observedAmount != null ? CostSource.TRANSACTION_OBSERVED : CostSource.UNKNOWN,
                    sub.getDeclaredFrequency(),
                    sub.getCurrency(), sub.getOrigin(),
                    sub.getNextDueDate(), sub.getStatusChangedAt()
            );
        }

        Long declaredAmount = sub.getDeclaredAmountMinor();
        Long observedAmount = null;
        RecurringSeries series = sub.getRecurringSeries();
        if (series != null) {
            observedAmount = series.getUsualAmountMinor();
        }

        CostSource source;
        Long amountMinor;
        RecurringFrequency frequency;

        if (declaredAmount != null && declaredAmount >= 0) {
            // Priorité 1 : montant déclaré
            amountMinor = declaredAmount;
            source = CostSource.USER_DECLARED;
            frequency = sub.getDeclaredFrequency();
        } else if (observedAmount != null && observedAmount >= 0) {
            // Priorité 2 : montant observé
            amountMinor = observedAmount;
            source = CostSource.TRANSACTION_OBSERVED;
            frequency = sub.getDeclaredFrequency() != null && sub.getDeclaredFrequency() != RecurringFrequency.UNKNOWN
                    ? sub.getDeclaredFrequency() : RecurringFrequency.UNKNOWN;
        } else {
            // Priorité 3 : inconnu
            amountMinor = null;
            source = CostSource.UNKNOWN;
            frequency = sub.getDeclaredFrequency();
        }

        return new SubscriptionCostSnapshot(
                sub.getId(), sub.getName(), sub.getProvider(),
                amountMinor, source, frequency,
                sub.getCurrency(), sub.getDeclaredStatus(), sub.getOrigin(),
                sub.getNextDueDate(), sub.getConfirmedAt(), sub.getStatusChangedAt()
        );
    }
}
