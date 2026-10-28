package com.eparly.api.category.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité représentant une catégorie de dépenses ou revenus.
 * Supporte une organisation hiérarchique simple (catégorie mère / sous-catégorie)
 * et distingue les catégories système globales des catégories personnalisées par l'utilisateur.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Column(name = "system_flag", nullable = false)
    private boolean systemFlag = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 30)
    private CategoryType categoryType = CategoryType.EXPENSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Category() {
    }

    /**
     * Crée une catégorie système par défaut (sans propriétaire spécifique).
     */
    public static Category createSystem(String name, CategoryType categoryType, Category parent) {
        Category category = new Category();
        category.setName(name);
        category.setCategoryType(categoryType);
        category.setParent(parent);
        category.setSystemFlag(true);
        category.setUser(null);
        return category;
    }

    /**
     * Crée une catégorie personnalisée appartenant à un utilisateur.
     */
    public static Category createUserCategory(User user, String name, CategoryType categoryType, Category parent) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setCategoryType(categoryType);
        category.setParent(parent);
        category.setSystemFlag(false);
        return category;
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

    public boolean isSystem() {
        return systemFlag;
    }

    public boolean isRoot() {
        return parent == null;
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

    public Category getParent() {
        return parent;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSystemFlag() {
        return systemFlag;
    }

    public void setSystemFlag(boolean systemFlag) {
        this.systemFlag = systemFlag;
    }

    public CategoryType getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(CategoryType categoryType) {
        this.categoryType = categoryType;
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
        if (!(o instanceof Category category)) return false;
        return id != null && Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", systemFlag=" + systemFlag +
                ", categoryType=" + categoryType +
                ", parentId=" + (parent != null ? parent.getId() : null) +
                '}';
    }
}
