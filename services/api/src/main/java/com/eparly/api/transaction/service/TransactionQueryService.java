package com.eparly.api.transaction.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.dto.TransactionResponse;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public TransactionQueryService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                                   UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<TransactionResponse> list(UUID userId, int page, int size, UUID accountId,
                                                  LocalDate startDate, LocalDate endDate,
                                                  TransactionType transactionType) {
        requireActiveUser(userId);
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size must be greater than or equal to 1");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }
        if (accountId != null && !accountRepository.existsByIdAndUserId(accountId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        Specification<Transaction> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.join("account").join("user").get("id"), userId));
            if (accountId != null) {
                predicates.add(builder.equal(root.get("account").get("id"), accountId));
            }
            if (startDate != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("bookingDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("bookingDate"), endDate));
            }
            if (transactionType != null) {
                predicates.add(builder.equal(root.get("transactionType"), transactionType));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Transaction> transactions = transactionRepository.findAll(specification,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                        Sort.by(Sort.Order.desc("bookingDate"), Sort.Order.desc("id"))));
        return PageResponse.from(transactions.map(TransactionResponse::from));
    }

    public TransactionResponse get(UUID userId, UUID id) {
        requireActiveUser(userId);
        return transactionRepository.findByIdAndUserId(id, userId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }
}
