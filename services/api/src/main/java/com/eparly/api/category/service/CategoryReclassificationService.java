package com.eparly.api.category.service;

import com.eparly.api.category.classification.CategoryClassificationService;
import com.eparly.api.category.domain.Category;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service de reclassement contrôlé des catégories automatiques.
 *
 * Ce traitement :
 * <ul>
 *   <li>ignore les transactions modifiées manuellement (customCategoryFlag = true)</li>
 *   <li>respecte les propriétaires (ne touche que les transactions de l'utilisateur)</li>
 *   <li>est idempotent (réécrire la même catégorie n'est pas fait)</li>
 *   <li>évite de réécrire les transactions inchangées</li>
 *   <li>ne réécrit pas les fingerprints</li>
 * </ul>
 *
 * N'est pas exécuté automatiquement au démarrage. Doit être invoqué explicitement.
 */
@Service
public class CategoryReclassificationService {

    private static final Logger log = LoggerFactory.getLogger(CategoryReclassificationService.class);

    private final TransactionRepository transactionRepository;
    private final CategoryClassificationService classificationService;

    public CategoryReclassificationService(
            TransactionRepository transactionRepository,
            CategoryClassificationService classificationService
    ) {
        this.transactionRepository = transactionRepository;
        this.classificationService = classificationService;
    }

    /**
     * Recalcule les catégories automatiques pour toutes les transactions d'un utilisateur.
     *
     * @param userId l'ID de l'utilisateur dont les transactions sont reclassées
     * @return le nombre de transactions effectivement modifiées
     */
    @Transactional
    public int reclassifyForUser(java.util.UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllByUserIdOrderByBookingDateDesc(userId);
        int changed = 0;

        for (Transaction tx : transactions) {
            // Ignorer les transactions modifiées manuellement
            if (tx.isCustomCategoryFlag()) {
                continue;
            }

            Optional<Category> newCategory = classificationService.classify(tx.getNormalizedLabel());

            // Si aucune règle ne s'applique, laisser sans catégorie
            if (newCategory.isEmpty()) {
                // Si la transaction avait une catégorie automatique, la retirer
                if (tx.getCategory() != null) {
                    tx.setCategory(null);
                    transactionRepository.save(tx);
                    changed++;
                }
                continue;
            }

            // Si la catégorie est déjà la bonne, ne pas réécrire
            if (tx.getCategory() != null && tx.getCategory().getId().equals(newCategory.get().getId())) {
                continue;
            }

            // Appliquer la nouvelle catégorie automatique
            tx.setCategory(newCategory.get());
            transactionRepository.save(tx);
            changed++;
        }

        log.info("Reclassification for user {}: {} transactions updated out of {}", userId, changed, transactions.size());
        return changed;
    }
}
