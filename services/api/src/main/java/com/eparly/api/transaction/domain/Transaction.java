package com.eparly.api.transaction.domain;

import com.eparly.api.account.domain.Account;
import com.eparly.api.category.domain.Category;
import com.eparly.api.imports.domain.ImportJob;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité représentant une transaction financière importée (dépense, revenu, virement).
 * Les montants sont obligatoirement stockés en centimes (amountMinor, entier BIGINT)
 * pour interdire toute erreur d'arrondi sur nombres flottants.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJob importJob;

    @Size(max = 128)
    @Column(name = "source_uid", length = 128)
    private String sourceUid;

    @NotBlank
    @Size(max = 64)
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @NotNull
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @NotNull
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @NotBlank
    @Size(max = 500)
    @Column(name = "raw_label", nullable = false, length = 500)
    private String rawLabel;

    @NotBlank
    @Size(max = 500)
    @Column(name = "normalized_label", nullable = false, length = 500)
    private String normalizedLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @Column(name = "custom_category_flag", nullable = false)
    private boolean customCategoryFlag = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType = TransactionType.UNKNOWN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Transaction() {
    }

    public Transaction(
            Account account,
            ImportJob importJob,
            String fingerprint,
            LocalDate bookingDate,
            Long amountMinor,
            String rawLabel,
            String normalizedLabel,
            TransactionType transactionType
    ) {
        this.account = account;
        this.importJob = importJob;
        this.fingerprint = fingerprint;
        this.bookingDate = bookingDate;
        this.amountMinor = amountMinor;
        this.rawLabel = rawLabel;
        this.normalizedLabel = normalizedLabel;
        this.transactionType = transactionType != null ? transactionType : TransactionType.UNKNOWN;
        this.currency = account != null ? account.getCurrency() : "EUR";
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Permet à l'utilisateur de classifier manuellement la transaction.
     * Positionne automatiquement le flag de customisation pour empêcher
     * un ré-écrasement par des moteurs de catégorisation automatique.
     */
    public void assignUserCategory(Category newCategory) {
        this.category = newCategory;
        this.customCategoryFlag = true;
    }

    public boolean isTransfer() {
        return this.transactionType == TransactionType.TRANSFER;
    }

    public boolean isExpense() {
        return this.transactionType == TransactionType.EXPENSE || this.transactionType == TransactionType.FEE;
    }

    public boolean isIncome() {
        return this.transactionType == TransactionType.INCOME || this.transactionType == TransactionType.REFUND;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public ImportJob getImportJob() {
        return importJob;
    }

    public void setImportJob(ImportJob importJob) {
        this.importJob = importJob;
    }

    public String getSourceUid() {
        return sourceUid;
    }

    public void setSourceUid(String sourceUid) {
        this.sourceUid = sourceUid;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRawLabel() {
        return rawLabel;
    }

    public void setRawLabel(String rawLabel) {
        this.rawLabel = rawLabel;
    }

    public String getNormalizedLabel() {
        return normalizedLabel;
    }

    public void setNormalizedLabel(String normalizedLabel) {
        this.normalizedLabel = normalizedLabel;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public boolean isCustomCategoryFlag() {
        return customCategoryFlag;
    }

    public void setCustomCategoryFlag(boolean customCategoryFlag) {
        this.customCategoryFlag = customCategoryFlag;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", bookingDate=" + bookingDate +
                ", amountMinor=" + amountMinor +
                ", currency='" + currency + '\'' +
                ", normalizedLabel='" + normalizedLabel + '\'' +
                ", transactionType=" + transactionType +
                ", customCategoryFlag=" + customCategoryFlag +
                '}';
    }
}
