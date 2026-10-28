package com.eparly.api.category.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.category.domain.Category;
import com.eparly.api.category.domain.CategoryType;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.dto.TransactionResponse;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service de modification manuelle de la catégorie d'une transaction.
 * Vérifie la propriété, la visibilité de la catégorie et la cohérence du type.
 */
@Service
public class CategoryTransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    public CategoryTransactionService(
            TransactionRepository transactionRepository,
            CategoryService categoryService,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    /**
     * Modifie manuellement la catégorie d'une transaction.
     *
     * @param userId    l'utilisateur authentifié (issu du JWT)
     * @param txId      l'ID de la transaction
     * @param categoryId l'ID de la nouvelle catégorie, ou null pour la supprimer
     * @return la transaction mise à jour sous forme de DTO
     */
    @Transactional
    public TransactionResponse assignCategory(UUID userId, UUID txId, UUID categoryId) {
        requireActiveUser(userId);

        // Vérifier que la transaction appartient à l'utilisateur
        Transaction transaction = transactionRepository.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));

        // Si suppression volontaire de la catégorie
        if (categoryId == null) {
            transaction.setCategory(null);
            transaction.setCustomCategoryFlag(true);
            transactionRepository.saveAndFlush(transaction);
            return TransactionResponse.from(transaction);
        }

        // Vérifier que la catégorie est visible par l'utilisateur
        Category category = categoryService.getVisibleCategory(categoryId, userId);

        // Valider la cohérence du type de catégorie
        validateCategoryTypeCompatibility(transaction, category);

        // Assigner la catégorie manuellement – cela positionne customCategoryFlag = true
        transaction.assignUserCategory(category);
        transactionRepository.saveAndFlush(transaction);
        return TransactionResponse.from(transaction);
    }

    /**
     * Vérifie qu'une catégorie peut être appliquée au type de transaction.
     * Une catégorie EXPENSE ne peut pas être appliquée à un revenu pur, et vice-versa.
     * Une catégorie BOTH est toujours compatible.
     */
    private void validateCategoryTypeCompatibility(Transaction transaction, Category category) {
        CategoryType catType = category.getCategoryType();
        if (catType == CategoryType.BOTH) {
            return; // Toujours compatible
        }

        TransactionType txType = transaction.getTransactionType();

        // UNKNOWN : on accepte n'importe quelle catégorie
        if (txType == TransactionType.UNKNOWN) {
            return;
        }

        boolean txIsExpense = txType == TransactionType.EXPENSE || txType == TransactionType.FEE;
        boolean txIsIncome = txType == TransactionType.INCOME || txType == TransactionType.REFUND;

        if (catType == CategoryType.EXPENSE && txIsIncome) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Category type EXPENSE is not compatible with an income transaction");
        }
        if (catType == CategoryType.INCOME && txIsExpense) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Category type INCOME is not compatible with an expense transaction");
        }
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }
}
