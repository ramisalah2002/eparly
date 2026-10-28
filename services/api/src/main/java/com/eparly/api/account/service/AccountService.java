package com.eparly.api.account.service;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.domain.AccountSourceType;
import com.eparly.api.account.dto.AccountResponse;
import com.eparly.api.account.dto.CreateAccountRequest;
import com.eparly.api.account.dto.UpdateAccountRequest;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        User user = requireActiveUser(userId);
        String name = request.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account name must contain 1 to 100 characters");
        }
        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this name already exists");
        }
        String currency = normalizeCurrency(request.currency());
        return AccountResponse.from(accountRepository.saveAndFlush(
                new Account(user, name, AccountSourceType.CSV, currency)));
    }

    public List<AccountResponse> list(UUID userId) {
        requireActiveUser(userId);
        return accountRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    public AccountResponse get(UUID userId, UUID accountId) {
        requireActiveUser(userId);
        return findOwnedAccount(userId, accountId).map(AccountResponse::from)
                .orElseThrow(() -> accountNotFound());
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        requireActiveUser(userId);
        Account account = findOwnedAccount(userId, accountId).orElseThrow(AccountService::accountNotFound);
        if (request.name() == null && request.currency() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one account field must be provided");
        }
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty() || name.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Account name must contain 1 to 100 characters");
            }
            if (accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, accountId)) {
                throw new ApiException(HttpStatus.CONFLICT, "An account with this name already exists");
            }
            account.setName(name);
        }
        if (request.currency() != null) {
            if (transactionRepository.existsByAccountId(accountId)) {
                throw new ApiException(HttpStatus.CONFLICT, "Account currency cannot be changed after transactions exist");
            }
            account.setCurrency(normalizeCurrency(request.currency()));
        }
        return AccountResponse.from(accountRepository.saveAndFlush(account));
    }

    private java.util.Optional<Account> findOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId);
    }

    private User requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? "EUR" : currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Account not found");
    }
}
