package com.eparly.api.category.classification;

import com.eparly.api.category.domain.Category;

import java.util.Optional;

/**
 * Règle de classification automatique d'une transaction vers une catégorie.
 *
 * Chaque règle déclare un niveau de spécificité (score) : plus le score est élevé,
 * plus la règle est prioritaire. Une règle qui ne s'applique pas retourne {@link Optional#empty()}.
 *
 * L'interface est conçue pour être extensible : règles système, règles personnalisées,
 * ou futures règles basées sur un modèle IA.
 */
public interface CategoryRule {

    /**
     * @return un score de spécificité (plus élevé = plus prioritaire).
     *         Les règles exactes doivent avoir un score supérieur aux règles par mots-clés.
     */
    int specificity();

    /**
     * Tente de classifier une transaction à partir de son libellé normalisé.
     *
     * @param normalizedLabel le libellé normalisé de la transaction (majuscules, sans accents)
     * @return la catégorie correspondante, ou {@link Optional#empty()} si la règle ne s'applique pas
     */
    Optional<Category> classify(String normalizedLabel);
}
