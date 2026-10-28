package com.eparly.api.imports.domain;

import com.eparly.api.account.domain.Account;
import com.eparly.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité représentant une opération d'import de transactions financières.
 * Conserve la traçabilité complète de l'import (source, métriques, erreurs éventuelles).
 */
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotBlank
    @Size(max = 64)
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @NotNull
    @PositiveOrZero
    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @NotNull
    @PositiveOrZero
    @Column(name = "inserted_rows", nullable = false)
    private int insertedRows = 0;

    @NotNull
    @PositiveOrZero
    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows = 0;

    @NotNull
    @PositiveOrZero
    @Column(name = "failed_rows", nullable = false)
    private int failedRows = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Size(max = 1000)
    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    public ImportJob() {
    }

    public ImportJob(User user, Account account, String fileHash) {
        this.user = user;
        this.account = account;
        this.fileHash = fileHash;
        this.status = ImportJobStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markCompleted(int totalRows, int insertedRows, int skippedRows, int failedRows) {
        this.status = ImportJobStatus.COMPLETED;
        this.totalRows = totalRows;
        this.insertedRows = insertedRows;
        this.skippedRows = skippedRows;
        this.failedRows = failedRows;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorSummary) {
        this.status = ImportJobStatus.FAILED;
        this.errorSummary = errorSummary;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public void setStatus(ImportJobStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public void setInsertedRows(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        this.skippedRows = skippedRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(int failedRows) {
        this.failedRows = failedRows;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImportJob job)) return false;
        return id != null && Objects.equals(id, job.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ImportJob{" +
                "id=" + id +
                ", status=" + status +
                ", totalRows=" + totalRows +
                ", insertedRows=" + insertedRows +
                ", skippedRows=" + skippedRows +
                ", failedRows=" + failedRows +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                '}';
    }
}
