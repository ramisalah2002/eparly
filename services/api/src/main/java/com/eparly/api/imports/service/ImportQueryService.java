package com.eparly.api.imports.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.dto.ImportJobResponse;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Historique des imports : toutes les lectures sont filtrees par l'utilisateur issu du JWT.
 * Un import ou un compte d'un autre utilisateur est indiscernable d'un element inexistant (404).
 */
@Service
@Transactional(readOnly = true)
public class ImportQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ImportJobRepository importJobRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public ImportQueryService(
            ImportJobRepository importJobRepository,
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.importJobRepository = importJobRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<ImportJobResponse> list(UUID userId, int page, int size) {
        requireActiveUser(userId);
        Page<ImportJob> jobs = importJobRepository.findByUserId(userId, pageable(page, size));
        return PageResponse.from(jobs.map(ImportJobResponse::from));
    }

    public PageResponse<ImportJobResponse> listForAccount(UUID userId, UUID accountId, int page, int size) {
        requireActiveUser(userId);
        if (!accountRepository.existsByIdAndUserId(accountId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        Page<ImportJob> jobs = importJobRepository.findByAccountIdAndUserId(accountId, userId, pageable(page, size));
        return PageResponse.from(jobs.map(ImportJobResponse::from));
    }

    public ImportJobResponse get(UUID userId, UUID importId) {
        requireActiveUser(userId);
        return importJobRepository.findByIdAndUserId(importId, userId)
                .map(ImportJobResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size must be greater than or equal to 1");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
