package com.eparly.api.imports.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Trace un import refuse dans l'historique, dans sa propre transaction : la trace subsiste meme si l'appelant annule.
 * Ne contient que des compteurs et un resume non sensible (codes d'erreur et nombres).
 */
@Component
public class ImportJobRecorder {

    private final ImportJobRepository importJobRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public ImportJobRecorder(
            ImportJobRepository importJobRepository,
            UserRepository userRepository,
            AccountRepository accountRepository
    ) {
        this.importJobRepository = importJobRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordFailure(UUID userId, UUID accountId, String fileHash, int totalRows, int failedRows, String summary) {
        ImportJob job = new ImportJob(userRepository.getReferenceById(userId),
                accountRepository.getReferenceById(accountId), fileHash);
        job.setTotalRows(totalRows);
        job.setFailedRows(Math.min(failedRows, totalRows));
        job.markFailed(summary.length() > 1000 ? summary.substring(0, 1000) : summary);
        return importJobRepository.saveAndFlush(job).getId();
    }
}
