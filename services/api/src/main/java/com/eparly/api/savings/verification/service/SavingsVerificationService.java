package com.eparly.api.savings.verification.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.repository.SavingsActionRepository;
import com.eparly.api.savings.verification.domain.EvidenceRole;
import com.eparly.api.savings.verification.domain.SavingsActionEvidence;
import com.eparly.api.savings.verification.domain.SavingsActionVerification;
import com.eparly.api.savings.verification.dto.AddEvidenceRequest;
import com.eparly.api.savings.verification.dto.EvaluateVerificationRequest;
import com.eparly.api.savings.verification.dto.EvidenceResponse;
import com.eparly.api.savings.verification.dto.ObservedPaymentSummary;
import com.eparly.api.savings.verification.dto.VerificationCandidatesResponse;
import com.eparly.api.savings.verification.dto.VerificationDetailsResponse;
import com.eparly.api.savings.verification.repository.SavingsActionEvidenceRepository;
import com.eparly.api.savings.verification.repository.SavingsActionVerificationRepository;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SavingsVerificationService {

    private final SavingsActionRepository actionRepository;
    private final TransactionRepository transactionRepository;
    private final SavingsActionEvidenceRepository evidenceRepository;
    private final SavingsActionVerificationRepository verificationRepository;
    private final SavingsTransactionMatchingService matchingService;
    private final SavingsVerificationEvaluator evaluator;
    private final Clock clock;

    public SavingsVerificationService(
            SavingsActionRepository actionRepository,
            TransactionRepository transactionRepository,
            SavingsActionEvidenceRepository evidenceRepository,
            SavingsActionVerificationRepository verificationRepository,
            SavingsTransactionMatchingService matchingService,
            SavingsVerificationEvaluator evaluator,
            @Autowired(required = false) Clock clock) {
        this.actionRepository = actionRepository;
        this.transactionRepository = transactionRepository;
        this.evidenceRepository = evidenceRepository;
        this.verificationRepository = verificationRepository;
        this.matchingService = matchingService;
        this.evaluator = evaluator;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public VerificationCandidatesResponse getCandidates(Jwt jwt, UUID actionId, EvidenceRole roleFilter, Pageable pageable) {
        UUID userId = JwtUserId.from(jwt);
        SavingsAction action = actionRepository.findByIdAndUserId(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Action d'économie introuvable"));

        return matchingService.findCandidates(userId, action, roleFilter, pageable);
    }

    public EvidenceResponse addEvidence(Jwt jwt, UUID actionId, AddEvidenceRequest request) {
        if (request == null || request.transactionId() == null || request.role() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requête d'association de preuve invalide");
        }

        UUID userId = JwtUserId.from(jwt);
        SavingsAction action = actionRepository.findByIdAndUserIdForUpdate(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Action d'économie introuvable"));

        // Contrôle d'isolation strict : la transaction doit appartenir au même utilisateur
        Transaction transaction = transactionRepository.findByIdAndUserId(request.transactionId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction bancaire introuvable"));

        if (action.getSubscription().getAccount() != null
                && !action.getSubscription().getAccount().getId().equals(transaction.getAccount().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La transaction doit appartenir au compte associé à l'abonnement");
        }

        // Exclusion stricte des non-dépenses (virements, salaires, remboursements)
        if (!transaction.isExpense()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Seules les dépenses ou frais bancaires peuvent constituer des preuves de paiement d'abonnement");
        }

        // Vérification de compatibilité de devise
        if (!transaction.getCurrency().equalsIgnoreCase(action.getSubscription().getCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La devise de la transaction (" + transaction.getCurrency() + ") ne correspond pas à celle de l'abonnement ("
                            + action.getSubscription().getCurrency() + ")");
        }

        // Unicité absolue de la transaction : une transaction ne peut servir qu'une seule fois
        if (evidenceRepository.existsByTransactionId(transaction.getId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cette transaction bancaire est déjà associée à une preuve financière");
        }

        if (request.effectiveChangeDate() != null) {
            action.setEffectiveChangeDate(request.effectiveChangeDate());
            action = actionRepository.save(action);
        }

        SavingsActionEvidence evidence = new SavingsActionEvidence(
                action.getUser(),
                action,
                transaction,
                request.role(),
                com.eparly.api.savings.verification.domain.EvidenceProvenance.USER_CONFIRMED,
                clock.instant()
        );

        try {
            evidence = evidenceRepository.saveAndFlush(evidence);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cette transaction bancaire ne peut être associée plusieurs fois");
        }

        evaluateInternal(action, userId);

        return toEvidenceResponse(evidence);
    }

    public void removeEvidence(Jwt jwt, UUID actionId, UUID evidenceId) {
        UUID userId = JwtUserId.from(jwt);
        SavingsAction action = actionRepository.findByIdAndUserIdForUpdate(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Action d'économie introuvable"));

        SavingsActionEvidence evidence = evidenceRepository.findByIdAndActionIdAndUserId(evidenceId, actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Preuve d'association introuvable"));

        evidenceRepository.delete(evidence);
        evidenceRepository.flush();

        evaluateInternal(action, userId);
    }

    public VerificationDetailsResponse getVerificationDetails(Jwt jwt, UUID actionId) {
        UUID userId = JwtUserId.from(jwt);
        SavingsAction action = actionRepository.findByIdAndUserIdForUpdate(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Action d'économie introuvable"));

        SavingsActionVerification verification = verificationRepository.findLatestByActionId(actionId)
                .orElseGet(() -> evaluateInternal(action, userId));

        return buildDetailsResponse(action, verification);
    }

    public VerificationDetailsResponse evaluate(Jwt jwt, UUID actionId, EvaluateVerificationRequest request) {
        UUID userId = JwtUserId.from(jwt);
        SavingsAction action = actionRepository.findByIdAndUserIdForUpdate(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Action d'économie introuvable"));

        if (request != null && request.effectiveChangeDate() != null) {
            action.setEffectiveChangeDate(request.effectiveChangeDate());
            action = actionRepository.save(action);
        }

        SavingsActionVerification verification = evaluateInternal(action, userId);
        return buildDetailsResponse(action, verification);
    }

    private SavingsActionVerification evaluateInternal(SavingsAction action, UUID userId) {
        List<SavingsActionEvidence> evidences = evidenceRepository.findAllByActionIdOrderByTransactionBookingDateAsc(action.getId());
        List<Transaction> userTransactions = transactionsForAction(action, userId);

        SavingsVerificationEvaluator.EvaluationResult result = evaluator.evaluate(action, evidences, userTransactions);

        // Mise à jour de l'action si le statut a changé
        if (action.getVerificationStatus() != result.status()) {
            action.setVerificationStatus(result.status());
            action = actionRepository.save(action);
        }

        // Idempotence : vérifier si le résultat est strictement identique au dernier audit
        Optional<SavingsActionVerification> latestOpt = verificationRepository.findLatestByActionId(action.getId());
        if (latestOpt.isPresent()) {
            SavingsActionVerification latest = latestOpt.get();
            if (latest.getStatus() == result.status()
                    && Objects.equals(latest.getOldMonthlyAmountMinor(), result.oldMonthlyAmountMinor())
                    && Objects.equals(latest.getNewMonthlyAmountMinor(), result.newMonthlyAmountMinor())
                    && Objects.equals(latest.getObservedDifferenceMinor(), result.observedDifferenceMinor())
                    && Objects.equals(latest.getCoveredFrom(), result.coveredFrom())
                    && Objects.equals(latest.getCoveredTo(), result.coveredTo())
                    && Objects.equals(latest.getMissingInformation(), result.missingInformation())
                    && Objects.equals(latest.getContradictions(), result.contradictions())
                    && Objects.equals(latest.getDecisionReasons(), result.decisionReasons())) {
                // Aucun changement réel : pas de création de ligne d'audit redondante
                return latest;
            }
        }

        SavingsActionVerification verification = new SavingsActionVerification(
                action,
                action.getUser(),
                result.status(),
                clock.instant(),
                result.oldMonthlyAmountMinor(),
                result.newMonthlyAmountMinor(),
                result.observedDifferenceMinor(),
                result.coveredFrom(),
                result.coveredTo(),
                result.missingInformation(),
                result.contradictions(),
                result.decisionReasons()
        );

        return verificationRepository.save(verification);
    }

    private VerificationDetailsResponse buildDetailsResponse(SavingsAction action, SavingsActionVerification verification) {
        List<SavingsActionEvidence> evidences = evidenceRepository.findAllByActionIdOrderByTransactionBookingDateAsc(action.getId());

        List<ObservedPaymentSummary> oldPayments = new ArrayList<>();
        List<ObservedPaymentSummary> newPayments = new ArrayList<>();

        for (SavingsActionEvidence e : evidences) {
            Transaction tx = e.getTransaction();
            ObservedPaymentSummary summary = new ObservedPaymentSummary(
                    e.getId(),
                    tx.getId(),
                    tx.getBookingDate(),
                    tx.getAmountMinor(),
                    tx.getCurrency(),
                    tx.getNormalizedLabel(),
                    e.getRole(),
                    e.getProvenance()
            );
            if (e.getRole() == EvidenceRole.OLD_SUBSCRIPTION_PAYMENT) {
                oldPayments.add(summary);
            } else {
                newPayments.add(summary);
            }
        }

        // Récupérer à nouveau la description d'écart vs estimation
        List<Transaction> userTransactions = transactionsForAction(action, action.getUser().getId());
        SavingsVerificationEvaluator.EvaluationResult evalResult = evaluator.evaluate(action, evidences, userTransactions);

        return new VerificationDetailsResponse(
                action.getId(),
                verification.getStatus(),
                action.getEffectiveChangeDate(),
                action.getDeclaredAt(),
                oldPayments,
                newPayments,
                verification.getOldMonthlyAmountMinor(),
                verification.getNewMonthlyAmountMinor(),
                verification.getObservedDifferenceMinor(),
                verification.getCoveredFrom(),
                verification.getCoveredTo(),
                verification.getMissingInformation(),
                verification.getContradictions(),
                verification.getDecisionReasons(),
                action.getEstimateSnapshot(),
                evalResult.differenceVersusEstimate()
        );
    }

    private EvidenceResponse toEvidenceResponse(SavingsActionEvidence evidence) {
        Transaction tx = evidence.getTransaction();
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getAction().getId(),
                tx.getId(),
                evidence.getRole(),
                evidence.getProvenance(),
                evidence.getConfirmedAt(),
                tx.getAmountMinor(),
                tx.getCurrency(),
                tx.getBookingDate(),
                tx.getNormalizedLabel()
        );
    }

    private List<Transaction> transactionsForAction(SavingsAction action, UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllByUserIdOrderByBookingDateDesc(userId);
        if (action.getSubscription().getAccount() == null) {
            return transactions;
        }
        UUID accountId = action.getSubscription().getAccount().getId();
        return transactions.stream()
                .filter(transaction -> accountId.equals(transaction.getAccount().getId()))
                .toList();
    }
}
