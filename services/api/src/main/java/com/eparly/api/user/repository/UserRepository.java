package com.eparly.api.user.repository;

import com.eparly.api.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour la gestion de la persistance des utilisateurs.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Recherche un utilisateur par email de façon insensible à la casse.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Vérifie si un utilisateur existe déjà avec cet email (insensible à la casse).
     */
    boolean existsByEmailIgnoreCase(String email);
}
