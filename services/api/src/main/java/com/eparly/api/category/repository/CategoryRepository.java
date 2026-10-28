package com.eparly.api.category.repository;

import com.eparly.api.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les catégories de flux financiers.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllBySystemFlagTrue();

    List<Category> findAllByUserId(UUID userId);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    @Query("select c from Category c where c.systemFlag = true or c.user.id = :userId order by c.name asc")
    List<Category> findVisibleCategoriesForUser(@Param("userId") UUID userId);

    List<Category> findAllByParentId(UUID parentId);
}
