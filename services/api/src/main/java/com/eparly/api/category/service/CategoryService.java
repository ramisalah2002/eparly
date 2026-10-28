package com.eparly.api.category.service;

import com.eparly.api.category.domain.Category;
import com.eparly.api.category.dto.CategoryResponse;
import com.eparly.api.category.repository.CategoryRepository;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eparly.api.auth.exception.ApiException;

import java.util.List;
import java.util.UUID;

/**
 * Service de consultation des catégories visibles par un utilisateur.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Retourne les catégories système ainsi que les catégories privées
     * appartenant à l'utilisateur authentifié.
     */
    public List<CategoryResponse> listVisible(UUID userId) {
        requireActiveUser(userId);
        List<Category> categories = categoryRepository.findVisibleCategoriesForUser(userId);
        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Vérifie qu'une catégorie est visible par l'utilisateur donné
     * (système ou privée de cet utilisateur).
     *
     * @return l'entité Category si visible
     * @throws ApiException sinon
     */
    public Category getVisibleCategory(UUID categoryId, UUID userId) {
        requireActiveUser(userId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
        if (category.isSystemFlag()) {
            return category;
        }
        if (category.getUser() != null && category.getUser().getId().equals(userId)) {
            return category;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Category is not accessible");
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }
}
