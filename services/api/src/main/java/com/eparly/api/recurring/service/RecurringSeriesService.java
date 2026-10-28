package com.eparly.api.recurring.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;
import com.eparly.api.recurring.dto.RecurringDecisionRequest;
import com.eparly.api.recurring.dto.RecurringOccurrenceResponse;
import com.eparly.api.recurring.dto.RecurringSeriesDetailResponse;
import com.eparly.api.recurring.dto.RecurringSeriesResponse;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringSeriesService {

    private final RecurringSeriesRepository repository;
    private final AccountRepository accountRepository;

    public RecurringSeriesService(RecurringSeriesRepository repository, AccountRepository accountRepository) {
        this.repository = repository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<RecurringSeriesResponse> list(UUID userId, UUID accountId, RecurringSeriesStatus status, RecurringSeriesType type) {
        if (accountId != null) {
            boolean ok = accountRepository.existsByIdAndUserId(accountId, userId);
            if (!ok) throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return repository.findAllByUserIdFiltered(userId, accountId, status, type).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecurringSeriesResponse> candidates(UUID userId, UUID accountId) {
        return list(userId, accountId, RecurringSeriesStatus.CANDIDATE, null);
    }

    @Transactional(readOnly = true)
    public RecurringSeriesDetailResponse detail(UUID userId, UUID id) {
        RecurringSeries s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring series not found"));
        List<RecurringOccurrenceResponse> occs = s.getTransactions().stream()
                .sorted(Comparator.comparing(com.eparly.api.transaction.domain.Transaction::getBookingDate))
                .map(t -> new RecurringOccurrenceResponse(
                        t.getId(),
                        t.getBookingDate(),
                        Math.abs(t.getAmountMinor()),
                        t.getCurrency(),
                        t.getRawLabel(),
                        t.getTransactionType().name()
                )).toList();
        return new RecurringSeriesDetailResponse(
                s.getId(),
                s.getAccount().getId(),
                s.getAccount().getName(),
                s.getNormalizedLabel(),
                s.getCurrency(),
                s.getFrequency(),
                s.getStatus(),
                s.getSeriesType(),
                s.getUsualAmountMinor(),
                s.getMinAmountMinor(),
                s.getMaxAmountMinor(),
                s.getOccurrenceCount(),
                s.getFirstOccurrenceDate(),
                s.getLastOccurrenceDate(),
                s.getNextEstimatedDate(),
                s.getMonthlyEquivalentMinor(),
                s.getConfidenceScore(),
                confidenceLevel(s.getConfidenceScore()),
                List.copyOf(s.getReasons()),
                occs,
                s.getLastAnalyzedAt(),
                s.getUserDecisionAt(),
                s.getDecisionNote(),
                s.getNormalizedLabel(),
                s.getSeriesType(),
                s.getConfidenceScore(),
                confidenceLevel(s.getConfidenceScore()),
                List.copyOf(s.getReasons())
        );
    }

    @Transactional
    public RecurringSeriesResponse decide(UUID userId, UUID id, RecurringDecisionRequest req) {
        if (req == null || req.status() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status is required");
        }
        if (req.status() != RecurringSeriesStatus.CONFIRMED && req.status() != RecurringSeriesStatus.DISMISSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status must be CONFIRMED or DISMISSED");
        }
        RecurringSeries s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring series not found"));
        s.setStatus(req.status());
        if (req.seriesType() != null) s.setSeriesType(req.seriesType());
        s.setUserDecisionAt(Instant.now());
        s.setDecisionNote(req.note());
        if (req.status() == RecurringSeriesStatus.DISMISSED) {
            s.setDismissedOccurrenceCount(s.getOccurrenceCount());
        }
        repository.save(s);
        return toResponse(s);
    }

    private RecurringSeriesResponse toResponse(RecurringSeries s) {
        return new RecurringSeriesResponse(
                s.getId(),
                s.getAccount().getId(),
                s.getAccount().getName(),
                s.getNormalizedLabel(),
                s.getCurrency(),
                s.getFrequency(),
                s.getStatus(),
                s.getSeriesType(),
                s.getUsualAmountMinor(),
                s.getMinAmountMinor(),
                s.getMaxAmountMinor(),
                s.getOccurrenceCount(),
                s.getFirstOccurrenceDate(),
                s.getLastOccurrenceDate(),
                s.getNextEstimatedDate(),
                s.getMonthlyEquivalentMinor(),
                s.getConfidenceScore(),
                confidenceLevel(s.getConfidenceScore()),
                s.getNormalizedLabel(),
                s.getSeriesType(),
                s.getConfidenceScore(),
                confidenceLevel(s.getConfidenceScore())
        );
    }

    private String confidenceLevel(int score) {
        if (score >= 75) return "HIGH";
        if (score >= 50) return "MEDIUM";
        return "LOW";
    }
}
