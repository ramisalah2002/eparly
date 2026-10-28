package com.eparly.api.imports.service;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.imports.dto.ImportIssueDto;
import com.eparly.api.imports.dto.ImportJobResponse;
import com.eparly.api.imports.dto.ImportMappingDto;
import com.eparly.api.imports.dto.ImportPreviewResponse;
import com.eparly.api.imports.dto.ImportResultResponse;
import com.eparly.api.imports.dto.ImportSampleRowDto;
import com.eparly.api.imports.validation.ImportIssue;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Orchestration des imports : autorisation (identite issue du JWT, compte du proprietaire) puis pipeline partage.
 * Le parsing se fait hors transaction ; seule la persistance ({@link ImportPersistenceService}) est transactionnelle.
 * Aucune donnee du fichier (libelle, montant) n'est journalisee.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final int FINGERPRINT_QUERY_BATCH = 1000;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ImportPipeline pipeline;
    private final ImportPersistenceService persistenceService;
    private final ImportJobRecorder jobRecorder;
    private final ImportProperties properties;

    public ImportService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ImportPipeline pipeline,
            ImportPersistenceService persistenceService,
            ImportJobRecorder jobRecorder,
            ImportProperties properties
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.pipeline = pipeline;
        this.persistenceService = persistenceService;
        this.jobRecorder = jobRecorder;
        this.properties = properties;
    }

    /** Previsualisation : lecture seule, aucune ecriture en base. */
    public ImportPreviewResponse preview(ImportCommand command) {
        Account account = authorizeAccount(command.userId(), command.accountId());
        String fileHash = FileHasher.sha256Hex(command.data());

        ImportAnalysis analysis = pipeline.analyze(command.data(), account.getCurrency(), command.charset(),
                command.delimiter(), command.mapping(), false);

        int wouldSkip = countExisting(account.getId(), analysis.candidates());
        int wouldInsert = analysis.candidates().size() - wouldSkip;

        int cap = properties.maxReportedIssues();
        List<ImportIssueDto> errors = cap(analysis.errors(), cap);
        List<ImportIssueDto> warnings = cap(analysis.warnings(), cap);
        int issueCount = analysis.issues().size();
        boolean truncated = errors.size() + warnings.size() < issueCount;

        List<ImportSampleRowDto> sample = analysis.candidates().stream()
                .limit(properties.previewSampleSize())
                .map(ImportSampleRowDto::from)
                .toList();

        return new ImportPreviewResponse(
                fileHash,
                analysis.csv().charset().name(),
                analysis.csv().charsetAmbiguous(),
                analysis.csv().charsetCandidates().stream().map(Enum::name).toList(),
                String.valueOf(analysis.csv().delimiter()),
                analysis.csv().headers(),
                ImportMappingDto.from(analysis.suggestedMapping()),
                ImportMappingDto.from(analysis.appliedMapping()),
                analysis.mappingValid(),
                analysis.totalRows(),
                wouldInsert,
                wouldSkip,
                analysis.blocking(),
                sample,
                errors,
                warnings,
                issueCount,
                truncated
        );
    }

    /**
     * Import confirme. Le serveur ne fait confiance a aucune donnee du client hormis le fichier lui-meme :
     * il recalcule le hash, reparse et revalide tout. Atomique : une erreur bloquante => aucune transaction.
     */
    public ImportResultResponse importFile(ImportCommand command) {
        Account account = authorizeAccount(command.userId(), command.accountId());

        if (command.fileHash() == null || !SHA256_HEX.matcher(command.fileHash()).matches()) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "FILE_HASH_REQUIRED",
                    "Le hash SHA-256 obtenu a la previsualisation est obligatoire");
        }
        if (command.mapping() == null) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "MAPPING_REQUIRED",
                    "Le mapping confirme est obligatoire");
        }
        String computedHash = FileHasher.sha256Hex(command.data());
        if (!computedHash.equals(command.fileHash())) {
            throw new ImportRequestException(HttpStatus.CONFLICT, "FILE_HASH_MISMATCH",
                    "Le fichier ne correspond pas a celui qui a ete previsualise : relancez la previsualisation");
        }

        ImportAnalysis analysis = pipeline.analyze(command.data(), account.getCurrency(), command.charset(),
                command.delimiter(), command.mapping(), true);

        if (analysis.csv().charsetAmbiguous()) {
            throw new ImportRequestException(HttpStatus.BAD_REQUEST, "CHARSET_REQUIRED",
                    "L'encodage du fichier est ambigu : precisez charset (WINDOWS_1252 ou ISO_8859_1)");
        }

        if (analysis.blocking()) {
            throw reject(command, account, computedHash, analysis);
        }

        ImportJobResponse job = persistenceService.persist(command.userId(), account.getId(), computedHash,
                analysis.totalRows(), analysis.candidates());
        return new ImportResultResponse(job, cap(analysis.warnings(), properties.maxReportedIssues()));
    }

    private ImportRejectedException reject(ImportCommand command, Account account, String fileHash, ImportAnalysis analysis) {
        List<ImportIssue> errors = analysis.errors();
        UUID jobId = jobRecorder.recordFailure(command.userId(), account.getId(), fileHash,
                analysis.totalRows(), analysis.invalidRows(), summarize(errors));
        log.info("Import rejected: job={}, errors={}", jobId, errors.size());
        int cap = properties.maxReportedIssues();
        return new ImportRejectedException(jobId, cap(errors, cap), errors.size(), errors.size() > cap);
    }

    /** Resume non sensible : uniquement des codes d'erreur et des nombres. */
    private static String summarize(List<ImportIssue> errors) {
        Map<String, Integer> byCode = new TreeMap<>();
        for (ImportIssue error : errors) {
            byCode.merge(error.code(), 1, Integer::sum);
        }
        StringBuilder summary = new StringBuilder(errors.size() + " erreur(s) bloquante(s) :");
        byCode.forEach((code, count) -> summary.append(' ').append(code).append(" x").append(count));
        return summary.toString();
    }

    private static List<ImportIssueDto> cap(List<ImportIssue> issues, int cap) {
        return issues.stream().limit(cap).map(ImportIssueDto::from).toList();
    }

    /**
     * Identite issue du JWT : l'utilisateur doit etre actif et le compte lui appartenir.
     * Un compte inconnu ou appartenant a un autre utilisateur renvoie le meme 404 neutre.
     */
    Account authorizeAccount(UUID userId, UUID accountId) {
        requireActiveUser(userId);
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }

    private int countExisting(UUID accountId, List<ImportCandidate> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }
        List<String> fingerprints = candidates.stream().map(ImportCandidate::fingerprint).toList();
        Set<String> existing = new HashSet<>();
        for (int from = 0; from < fingerprints.size(); from += FINGERPRINT_QUERY_BATCH) {
            List<String> batch = new ArrayList<>(
                    fingerprints.subList(from, Math.min(from + FINGERPRINT_QUERY_BATCH, fingerprints.size())));
            existing.addAll(transactionRepository.findExistingFingerprints(accountId, batch));
        }
        int skipped = 0;
        for (String fingerprint : fingerprints) {
            if (existing.contains(fingerprint)) {
                skipped++;
            }
        }
        return skipped;
    }
}
